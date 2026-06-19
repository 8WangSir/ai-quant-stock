package com.quant.market.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;
import com.quant.common.entity.SyncProgress;
import com.quant.market.config.QuantProperties;
import com.quant.market.datasource.MarketDataProvider;
import com.quant.market.datasource.baostock.BaostockClient;
import com.quant.market.datasource.infoway.InfowayProvider;
import com.quant.market.mapper.StockDailyMapper;
import com.quant.market.mapper.StockFinanceMapper;
import com.quant.market.mapper.StockInfoMapper;
import com.quant.market.mapper.SyncProgressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private static final int BATCH_SIZE = 500;
    private static final int FINANCE_BATCH_SIZE = 100;

    private final MarketDataProvider dataProvider;
    private final StockInfoMapper stockInfoMapper;
    private final StockDailyMapper stockDailyMapper;
    private final StockFinanceMapper stockFinanceMapper;
    private final SyncProgressMapper syncProgressMapper;
    private final JdbcTemplate jdbcTemplate;
    private final QuantProperties properties;

    @Autowired(required = false)
    private InfowayProvider infowayProvider;

    private final BaostockClient baostockClient;

    // ==================== 股票列表 ====================

    @Transactional
    public void syncStockList() {
        List<StockInfo> stocks;
        
        if (baostockClient != null) {
            List<Map<String, Object>> data = baostockClient.fetchStockList();
            if (data != null && !data.isEmpty()) {
                stocks = data.stream()
                        .map(this::mapToStockInfo)
                        .filter(s -> s != null)
                        .collect(Collectors.toList());
            } else {
                stocks = dataProvider.fetchStockList();
            }
        } else {
            stocks = dataProvider.fetchStockList();
        }
        
        for (StockInfo stock : stocks) {
            StockInfo existing = stockInfoMapper.selectOne(
                    new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getCode, stock.getCode()));
            if (existing == null) {
                stockInfoMapper.insert(stock);
            } else {
                stock.setId(existing.getId());
                stockInfoMapper.updateById(stock);
            }
        }
        log.info("Synced {} stocks", stocks.size());
    }

    // ==================== 日线数据 ====================

    /**
     * 全量同步日线：近一年历史数据
     * 使用 BaoStock 批量接口
     */
    public void syncDailyDataFull(LocalDate start, LocalDate end) {
        List<StockInfo> stocks = stockInfoMapper.selectList(null);
        int total = stocks.size();
        
        if (baostockClient != null) {
            log.info("【全量】日线同步开始: {} - {}", start, end);
            List<String> codes = stocks.stream().map(StockInfo::getCode).collect(Collectors.toList());
            List<Map<String, Object>> rawData = baostockClient.fetchDailyBars(codes, start.toString(), end.toString());
            
            if (rawData != null && !rawData.isEmpty()) {
                List<StockDaily> allBars = rawData.stream()
                        .map(this::mapToStockDaily)
                        .filter(f -> f != null)
                        .collect(Collectors.toList());
                
                for (int i = 0; i < allBars.size(); i += BATCH_SIZE) {
                    List<StockDaily> batch = allBars.subList(i, Math.min(i + BATCH_SIZE, allBars.size()));
                    stockDailyMapper.upsertBatch(batch);
                }
                log.info("【全量】日线同步完成: {} 条K线", allBars.size());
            }
        } else {
            syncDailyDataFallback(start, end);
        }
    }

    /**
     * 全量同步日线（支持断点续传）
     */
    public void syncDailyDataFull(LocalDate start, LocalDate end, int startIndex) {
        List<StockInfo> stocks = stockInfoMapper.selectList(null);
        int total = stocks.size();
        
        // 从指定索引开始
        List<StockInfo> pendingStocks = stocks.subList(Math.min(startIndex, total), total);
        try {
            log.info("【全量】日线同步（断点续传）: 开始索引 {}, 待同步 {} 只", startIndex, pendingStocks.size());

            if (baostockClient != null) {
                List<String> codes = pendingStocks.stream().map(StockInfo::getCode).collect(Collectors.toList());

                // 分批处理，每批100只，方便保存进度
                for (int batchIdx = 0; batchIdx < codes.size(); batchIdx += 100) {
                    List<String> batchCodes = codes.subList(batchIdx, Math.min(batchIdx + 100, codes.size()));
                    int currentStartIndex = startIndex + batchIdx;

                    log.info("【全量】日线同步批次: {}/{} (索引 {})",
                            batchIdx / 100 + 1,
                            (codes.size() + 99) / 100,
                            currentStartIndex);

                    List<Map<String, Object>> rawData = baostockClient.fetchDailyBars(batchCodes, start.toString(), end.toString());

                    if (rawData != null && !rawData.isEmpty()) {
                        List<StockDaily> allBars = rawData.stream()
                                .map(this::mapToStockDaily)
                                .filter(f -> f != null)
                                .collect(Collectors.toList());

                        for (int i = 0; i < allBars.size(); i += BATCH_SIZE) {
                            List<StockDaily> batch = allBars.subList(i, Math.min(i + BATCH_SIZE, allBars.size()));
                            stockDailyMapper.upsertBatch(batch);
                        }
                    }

                    // 保存进度
                    saveSyncProgress("daily_full", currentStartIndex + batchCodes.size(), total);
                }
            } else {
                syncDailyDataFallback(start, end);
            }
        } catch (Exception e) {
            log.error("【全量】日线同步失败: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 增量同步日线：只拉取指定日期的数据
     * 使用 BaoStock 批量接口
     */
    public void syncDailyDataIncr(LocalDate tradeDate) {
        List<StockInfo> stocks = stockInfoMapper.selectList(null);
        int total = stocks.size();

        Set<String> existingCodes = new HashSet<>();
        jdbcTemplate.queryForList(
                "SELECT DISTINCT code FROM stock_daily WHERE trade_date = ?",
                tradeDate).forEach(row -> existingCodes.add(row.get("code").toString()));

        log.info("【增量】日线同步: {}只, 日期={}, 已有{}只", total, tradeDate, existingCodes.size());

        if (baostockClient != null) {
            List<String> codes = stocks.stream()
                    .map(StockInfo::getCode)
                    .filter(c -> !existingCodes.contains(c))
                    .collect(Collectors.toList());

            if (codes.isEmpty()) {
                log.info("【增量】日线同步: 所有股票数据已是最新");
                return;
            }

            List<String> bsCodes = codes.stream().map(this::toBaostockCode).collect(Collectors.toList());
            List<Map<String, Object>> rawData = baostockClient.fetchDailyBarsIncr(bsCodes, tradeDate.toString());
            List<StockDaily> allBars = rawData.stream().map(this::mapToStockDaily).filter(f -> f != null).collect(Collectors.toList());

            for (int i = 0; i < allBars.size(); i += BATCH_SIZE) {
                List<StockDaily> batch = allBars.subList(i, Math.min(i + BATCH_SIZE, allBars.size()));
                stockDailyMapper.upsertBatch(batch);
            }
            log.info("【增量】日线同步完成: {}只新增, {}条K线", codes.size(), allBars.size());
        } else {
            syncDailyDataFallback(tradeDate, tradeDate);
        }
    }

    private void syncDailyDataFallback(LocalDate start, LocalDate end) {
        List<StockInfo> stocks = stockInfoMapper.selectList(null);
        int total = stocks.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<StockDaily> batch = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            StockInfo stock = stocks.get(i);
            try {
                List<StockDaily> bars = dataProvider.fetchDailyBars(stock.getCode(), start, end);
                if (bars != null && !bars.isEmpty()) {
                    batch.addAll(bars);
                    successCount.incrementAndGet();
                }
                if (batch.size() >= BATCH_SIZE) {
                    stockDailyMapper.upsertBatch(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
            if ((i + 1) % 100 == 0) {
                log.info("日线同步: {}/{} (成功{}, 失败{})", i + 1, total, successCount.get(), failCount.get());
            }
        }
        if (!batch.isEmpty()) {
            stockDailyMapper.upsertBatch(batch);
        }
    }

    // ==================== 财务数据 ====================

    /**
     * 全量同步财务：使用 BaoStock 批量获取最新一期
     */
    public void syncFinanceDataFull() {
        if (baostockClient == null) {
            log.warn("BaoStock 客户端未初始化，跳过财务同步");
            return;
        }

        List<StockInfo> stocks = stockInfoMapper.selectList(null);
        int total = stocks.size();

        // 断点续传
        Set<String> existingCodes = new HashSet<>();
        jdbcTemplate.queryForList("SELECT DISTINCT code FROM stock_finance")
                .forEach(row -> existingCodes.add(row.get("code").toString()));

        List<String> pendingCodes = stocks.stream()
                .map(StockInfo::getCode)
                .filter(c -> !existingCodes.contains(c))
                .collect(Collectors.toList());

        log.info("【全量】财务同步: 总{}只, 已有{}只, 待同步{}只", total, existingCodes.size(), pendingCodes.size());

        syncFinanceWithBaostock(pendingCodes);
    }

    /**
     * 增量同步财务：只同步指定季度的财报更新
     */
    public void syncFinanceDataIncr(int year, int quarter) {
        if (baostockClient == null) {
            log.warn("BaoStock 客户端未初始化，跳过财务同步");
            return;
        }

        List<StockInfo> stocks = stockInfoMapper.selectList(null);
        List<String> codes = stocks.stream().map(StockInfo::getCode).collect(Collectors.toList());

        log.info("【增量】财务同步: {}只, 年份={}, 季度={}", codes.size(), year, quarter);
        syncFinanceWithBaostockIncr(codes, year, quarter);
    }

    private void syncFinanceWithBaostock(List<String> codes) {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // BaoStock 需要 sh./sz. 前缀
        List<String> bsCodes = codes.stream()
                .map(this::toBaostockCode)
                .collect(Collectors.toList());

        // 分批处理，每批100只
        for (int i = 0; i < bsCodes.size(); i += 100) {
            List<String> batch = bsCodes.subList(i, Math.min(i + 100, bsCodes.size()));
            try {
//                List<Map<String, Object>> data = baostockClient.fetchFinanceData(batch);
//                if (data != null && !data.isEmpty()) {
//                    List<StockFinance> finances = data.stream()
//                            .map(this::mapToStockFinance)
//                            .filter(f -> f != null)
//                            .collect(Collectors.toList());
//                    if (!finances.isEmpty()) {
//                        stockFinanceMapper.upsertBatch(finances);
//                        successCount.addAndGet(finances.size());
//                        log.info("【全量】财务写入: {}/{} (本批{}条)", successCount.get(), bsCodes.size(), finances.size());
//                    }
//                }
            } catch (Exception e) {
                failCount.addAndGet(batch.size());
                log.error("【全量】财务批量失败 offset={}: {}", i, e.getMessage());
            }
        }
        log.info("【全量】财务同步完成: 成功{}, 失败{}", successCount.get(), failCount.get());
    }

    private void syncFinanceWithBaostockIncr(List<String> codes, int year, int quarter) {
        AtomicInteger successCount = new AtomicInteger(0);

        List<String> bsCodes = codes.stream()
                .map(this::toBaostockCode)
                .collect(Collectors.toList());

        for (int i = 0; i < bsCodes.size(); i += 100) {
            List<String> batch = bsCodes.subList(i, Math.min(i + 100, bsCodes.size()));
            try {
//                List<Map<String, Object>> data = baostockClient.fetchFinanceDataIncr(batch, year, quarter);
//                if (data != null && !data.isEmpty()) {
//                    List<StockFinance> finances = data.stream()
//                            .map(this::mapToStockFinance)
//                            .filter(f -> f != null)
//                            .collect(Collectors.toList());
//                    if (!finances.isEmpty()) {
//                        stockFinanceMapper.upsertBatch(finances);
//                        successCount.addAndGet(finances.size());
//                    }
//                }
            } catch (Exception e) {
                log.error("【增量】财务批量失败 offset={}: {}", i, e.getMessage());
            }
            if ((i + 100) % 500 == 0) {
                log.info("【增量】财务进度: {}/{} (成功{})", Math.min(i + 100, bsCodes.size()), bsCodes.size(), successCount.get());
            }
        }
        log.info("【增量】财务同步完成: 成功{}", successCount.get());
    }

    private void saveSyncProgress(String type, int current, int total) {
        SyncProgress progress = syncProgressMapper.selectOne(
                new LambdaQueryWrapper<SyncProgress>().eq(SyncProgress::getType, type));
        
        if (progress == null) {
            progress = new SyncProgress();
            progress.setType(type);
            progress.setCurrent(current);
            progress.setTotal(total);
            syncProgressMapper.insert(progress);
        } else {
            progress.setCurrent(current);
            progress.setTotal(total);
            syncProgressMapper.updateById(progress);
        }
    }

    private StockInfo mapToStockInfo(Map<String, Object> data) {
        try {
            StockInfo s = new StockInfo();
            s.setCode((String) data.get("code"));
            s.setName((String) data.get("name"));
            s.setMarket((String) data.get("exchange"));
            s.setIndustry((String) data.get("industry"));
            String listDate = (String) data.get("list_date");
            if (listDate != null) {
                s.setListDate(LocalDate.parse(listDate));
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private StockDaily mapToStockDaily(Map<String, Object> data) {
        try {
            StockDaily d = new StockDaily();
            String code = (String) data.get("code");
            if (code != null && code.contains(".")) {
                code = code.split("\\.")[1];
            }
            d.setCode(code);
            String tradeDate = (String) data.get("date");
            if (tradeDate != null) {
                d.setTradeDate(LocalDate.parse(tradeDate));
            }
            d.setOpen(toDecimal(data.get("open")));
            d.setHigh(toDecimal(data.get("high")));
            d.setLow(toDecimal(data.get("low")));
            d.setClose(toDecimal(data.get("close")));
            Object vol = data.get("volume");
            if (vol != null) {
                try {
                    d.setVolume(Long.valueOf(vol.toString()));
                } catch (NumberFormatException e) {
                    d.setVolume(null);
                }
            }
            d.setAmount(toDecimal(data.get("amount")));
            d.setTurnoverRate(toDecimal(data.get("turnover_rate")));
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private StockFinance mapToStockFinance(Map<String, Object> data) {
        try {
            StockFinance f = new StockFinance();
            f.setCode((String) data.get("code"));
            String reportDate = (String) data.get("report_date");
            if (reportDate != null) {
                f.setReportDate(LocalDate.parse(reportDate));
            }
            f.setRoe(toDecimal(data.get("roe")));
            f.setRevenueGrowth(toDecimal(data.get("revenue_growth")));
            f.setProfitGrowth(toDecimal(data.get("profit_growth")));
            f.setDebtRatio(toDecimal(data.get("debt_ratio")));
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private String toBaostockCode(String code) {
        if (code.startsWith("6") || code.startsWith("9")) {
            return "sh." + code;
        } else if (code.startsWith("4") || code.startsWith("8")) {
            return "bj." + code;
        } else {
            return "sz." + code;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 行业数据（暂不实现） ====================

    @Transactional
    public void syncIndustryData(LocalDate tradeDate) {
        log.info("行业数据同步暂未实现");
    }

    // ==================== 资金流（暂不实现） ====================

    @Transactional
    public void syncCapitalFlow(LocalDate tradeDate) {
        log.info("资金流同步暂未实现");
    }
}
