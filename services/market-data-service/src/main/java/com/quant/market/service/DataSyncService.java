package com.quant.market.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.entity.IndustryDaily;
import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;
import com.quant.common.entity.SyncProgress;
import com.quant.market.config.QuantProperties;
import com.quant.market.datasource.MarketDataProvider;
import com.quant.market.datasource.baostock.BaostockClient;
import com.quant.market.datasource.infoway.InfowayProvider;
import com.quant.market.mapper.IndustryDailyMapper;
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
import java.time.LocalDateTime;
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
    private final IndustryDailyMapper industryDailyMapper;
    private final JdbcTemplate jdbcTemplate;
    private final QuantProperties properties;

    @Autowired(required = false)
    private InfowayProvider infowayProvider;

    private final BaostockClient baostockClient;

    public LocalDate getLatestDailyDate() {
        String sql = "SELECT MAX(trade_date) FROM stock_daily";
        return jdbcTemplate.queryForObject(sql, LocalDate.class);
    }

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
                stock.setUpdateTime(LocalDateTime.now());
                stockInfoMapper.updateById(stock);
            }
        }
        log.info("Synced {} stocks", stocks.size());
    }

    /**
     * 同步行业数据
     */
    public void syncIndustryData() {
        if (baostockClient == null) {
            log.warn("BaoStock 客户端未初始化，跳过行业同步");
            return;
        }
        
        List<Map<String, Object>> industryData = baostockClient.fetchIndustryData();
        if (industryData == null || industryData.isEmpty()) {
            log.warn("未获取到行业数据");
            return;
        }
        
        int updated = 0;
        for (Map<String, Object> item : industryData) {
            String code = (String) item.get("code");
            String industry = (String) item.get("industry");
            
            if (code != null && industry != null && !industry.isEmpty()) {
                int rows = jdbcTemplate.update(
                        "UPDATE stock_info SET industry = ? WHERE code = ?",
                        industry, code);
                if (rows > 0) {
                    updated++;
                }
            }
        }
        
        log.info("【行业同步】完成: 更新 {} 只股票的行业数据", updated);
    }

    /**
     * 同步行业每日行情数据
     * 通过股票数据聚合计算行业行情，无需外部数据源
     */
    public void syncIndustryDaily(LocalDate startDate, LocalDate endDate) {
        log.info("【行业行情同步】开始: {} ~ {}", startDate, endDate);
        
        List<String> industries = jdbcTemplate.queryForList(
                "SELECT DISTINCT industry FROM stock_info WHERE industry IS NOT NULL AND industry != ''",
                String.class);
        
        if (industries.isEmpty()) {
            log.warn("未找到行业数据，请先同步股票列表");
            return;
        }
        
        log.info("【行业行情同步】共 {} 个行业", industries.size());
        
        int totalCount = 0;
        LocalDate currentDate = startDate;
        
        while (!currentDate.isAfter(endDate)) {
            if (currentDate.getDayOfWeek().getValue() > 5) {
                currentDate = currentDate.plusDays(1);
                continue;
            }
            
            log.info("【行业行情同步】同步日期: {}", currentDate);
            
            for (String industry : industries) {
                try {
                    List<Map<String, Object>> stockData = jdbcTemplate.queryForList(
                            "SELECT close, amount FROM stock_daily d " +
                            "JOIN stock_info i ON d.code = i.code " +
                            "WHERE i.industry = ? AND d.trade_date = ?",
                            industry, currentDate);
                    
                    if (stockData.isEmpty()) {
                        continue;
                    }
                    
                    BigDecimal totalClose = BigDecimal.ZERO;
                    BigDecimal totalAmount = BigDecimal.ZERO;
                    int count = 0;
                    
                    for (Map<String, Object> row : stockData) {
                        BigDecimal close = toDecimal(row.get("close"));
                        BigDecimal amount = toDecimal(row.get("amount"));
                        if (close != null && close.compareTo(BigDecimal.ZERO) > 0) {
                            totalClose = totalClose.add(close);
                            count++;
                        }
                        if (amount != null) {
                            totalAmount = totalAmount.add(amount);
                        }
                    }
                    
                    if (count == 0) {
                        continue;
                    }
                    
                    BigDecimal avgClose = totalClose.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP);
                    
                    List<Map<String, Object>> prevData = jdbcTemplate.queryForList(
                            "SELECT close, amount FROM stock_daily d " +
                            "JOIN stock_info i ON d.code = i.code " +
                            "WHERE i.industry = ? AND d.trade_date = ?",
                            industry, currentDate.minusDays(1));
                    
                    BigDecimal prevAvgClose = BigDecimal.ZERO;
                    if (!prevData.isEmpty()) {
                        BigDecimal prevTotal = BigDecimal.ZERO;
                        int prevCount = 0;
                        for (Map<String, Object> row : prevData) {
                            BigDecimal close = toDecimal(row.get("close"));
                            if (close != null && close.compareTo(BigDecimal.ZERO) > 0) {
                                prevTotal = prevTotal.add(close);
                                prevCount++;
                            }
                        }
                        if (prevCount > 0) {
                            prevAvgClose = prevTotal.divide(BigDecimal.valueOf(prevCount), 4, java.math.RoundingMode.HALF_UP);
                        }
                    }
                    
                    BigDecimal changePercent = BigDecimal.ZERO;
                    if (prevAvgClose.compareTo(BigDecimal.ZERO) > 0) {
                        changePercent = avgClose.subtract(prevAvgClose)
                                .divide(prevAvgClose, 4, java.math.RoundingMode.HALF_UP);
                    }
                    
                    jdbcTemplate.update(
                            "INSERT INTO industry_daily (industry_name, trade_date, close, change_percent, amount, capital_flow, create_time, update_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (industry_name, trade_date) DO UPDATE SET " +
                            "close = EXCLUDED.close, change_percent = EXCLUDED.change_percent, " +
                            "amount = EXCLUDED.amount, capital_flow = EXCLUDED.capital_flow, update_time = CURRENT_TIMESTAMP",
                            industry, currentDate, avgClose, changePercent, totalAmount, BigDecimal.ZERO);
                    
                    totalCount++;
                } catch (Exception e) {
                    log.warn("计算行业数据失败: {}", industry, e);
                }
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        log.info("【行业行情同步】完成: 共 {} 条记录", totalCount);
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
     * 全量同步财务：使用 BaoStock 批量获取最近一年
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

        // 同步最近一年的财务数据（4个季度）
        int currentYear = LocalDate.now().getYear();
        int startYear = currentYear - 1;  // 从去年开始
        syncFinanceWithBaostock(pendingCodes, startYear, currentYear);
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

    /**
     * 同步财务数据（支持指定年份范围）
     */
    private void syncFinanceWithBaostock(List<String> codes, int startYear, int endYear) {
        if (codes.isEmpty()) {
            log.info("【全量】财务同步: 没有待同步的股票");
            return;
        }
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 分批处理，每批100只
        for (int i = 0; i < codes.size(); i += 100) {
            List<String> batch = codes.subList(i, Math.min(i + 100, codes.size()));
            try {
                List<Map<String, Object>> data = baostockClient.syncFinanceDataFull(batch, startYear, endYear);
                if (data != null && !data.isEmpty()) {
                    List<StockFinance> finances = data.stream()
                            .map(this::mapToStockFinance)
                            .filter(f -> f != null)
                            .collect(Collectors.toList());
                    if (!finances.isEmpty()) {
                        stockFinanceMapper.upsertBatch(finances);
                        successCount.addAndGet(finances.size());
                        log.info("【全量】财务写入: {}/{} (本批{}条)", successCount.get(), codes.size(), finances.size());
                    }
                }
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
                // 单季度增量同步
                List<Map<String, Object>> data = baostockClient.syncFinanceDataFull(batch, year, year);
                if (data != null && !data.isEmpty()) {
                    List<StockFinance> finances = data.stream()
                            .map(this::mapToStockFinance)
                            .filter(f -> f != null)
                            .collect(Collectors.toList());
                    if (!finances.isEmpty()) {
                        stockFinanceMapper.upsertBatch(finances);
                        successCount.addAndGet(finances.size());
                    }
                }
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
            f.setCashFlow(toDecimal(data.get("cash_flow")));
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

    private IndustryDaily mapToIndustryDaily(Map<String, Object> data) {
        try {
            IndustryDaily d = new IndustryDaily();
            d.setIndustryName((String) data.get("industry_name"));
            String tradeDate = (String) data.get("trade_date");
            if (tradeDate != null) {
                d.setTradeDate(LocalDate.parse(tradeDate));
            }
            d.setClose(toDecimal(data.get("close")));
            d.setChangePercent(toDecimal(data.get("change_percent")));
            d.setAmount(toDecimal(data.get("amount")));
            d.setCapitalFlow(toDecimal(data.get("capital_flow")));
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        try {
            BigDecimal result = new BigDecimal(value.toString());
            // numeric(8,4) 最大绝对值 9999.9999，超出返回 null
            if (result.abs().compareTo(new BigDecimal("9999.9999")) > 0) {
                log.warn("【精度过滤】字段值 {} 超出范围 (abs > 9999.9999)，设为 null", value);
                return null;
            }
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== AKShare 财务数据同步 ====================

    private String getPythonScriptPath(String scriptName) {
        String configuredPath = properties.getSync().getPythonPath();
        String basePath;
        
        // 如果配置了环境变量或配置文件路径，直接使用
        if (configuredPath != null && !configuredPath.isEmpty()) {
            basePath = configuredPath;
        } else {
            // 自动检测：根据 user.dir 推断
            String userDir = System.getProperty("user.dir").replace("\\", "/");
            int servicesIdx = userDir.indexOf("/services/");
            if (servicesIdx > 0) {
                basePath = userDir.substring(0, servicesIdx) + "/python/data_sync";
            } else {
                // fallback: 假设在项目根目录
                basePath = userDir + "/python/data_sync";
            }
        }
        
        return (basePath.replace("\\", "/") + "/" + scriptName).replace("//", "/");
    }

    /**
     * 使用 AKShare 同步最近4个季度财务数据
     */
    public void syncFinanceAkshareRecent() {
        log.info("【AKShare财务同步】开始同步最近4个季度财务数据");
        try {
            String scriptPath = getPythonScriptPath("akshare_finance.py");
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    scriptPath,
                    "finance_recent"
            );
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[AKShare] {}", line);
                output.append(line);
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // 解析 JSON 结果 - 找到最后一个完整的 JSON 对象
                String jsonStr = output.toString();

                // 找到最后一个 "success" 字段所在的对象
                int lastSuccessIdx = jsonStr.lastIndexOf("\"success\"");
                if (lastSuccessIdx > 0) {
                    // 向前找该对象的起始位置
                    int startIdx = jsonStr.lastIndexOf("{", lastSuccessIdx);
                    if (startIdx >= 0) {
                        jsonStr = jsonStr.substring(startIdx);
                    }
                }

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> result = mapper.readValue(jsonStr, Map.class);

                Boolean success = (Boolean) result.get("success");
                if (Boolean.TRUE.equals(success)) {
                    List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                    if (data != null && !data.isEmpty()) {
                        saveFinanceData(data);
                        log.info("【AKShare财务同步】完成: 共 {} 条记录", data.size());
                    }
                } else {
                    log.error("【AKShare财务同步】失败: {}", result.get("error"));
                }
            } else {
                log.error("【AKShare财务同步】进程退出码: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("【AKShare财务同步】异常", e);
        }
    }

    /**
     * 使用 AKShare 同步指定季度财务数据
     */
    public void syncFinanceAkshare(String date) {
        log.info("【AKShare财务同步】开始同步 {} 财务数据", date);
        try {
            String scriptPath = getPythonScriptPath("akshare_finance.py");
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    scriptPath,
                    "finance_batch",
                    date
            );
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[AKShare] {}", line);
                output.append(line);
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // 解析 JSON 结果 - 找到最后一个完整的 JSON 对象
                String jsonStr = output.toString();

                // 找到最后一个 "success" 字段所在的对象
                int lastSuccessIdx = jsonStr.lastIndexOf("\"success\"");
                if (lastSuccessIdx > 0) {
                    // 向前找该对象的起始位置
                    int startIdx = jsonStr.lastIndexOf("{", lastSuccessIdx);
                    if (startIdx >= 0) {
                        jsonStr = jsonStr.substring(startIdx);
                    }
                }

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> result = mapper.readValue(jsonStr, Map.class);

                Boolean success = (Boolean) result.get("success");
                if (Boolean.TRUE.equals(success)) {
                    List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                    if (data != null && !data.isEmpty()) {
                        saveFinanceData(data);
                        log.info("【AKShare财务同步】完成: 共 {} 条记录", data.size());
                    }
                } else {
                    log.error("【AKShare财务同步】失败: {}", result.get("error"));
                }
            } else {
                log.error("【AKShare财务同步】进程退出码: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("【AKShare财务同步】异常", e);
        }
    }

    /**
     * 批量保存财务数据
     */
    private void saveFinanceData(List<Map<String, Object>> data) {
        int count = 0;
        for (Map<String, Object> item : data) {
            try {
                String code = (String) item.get("code");
                String reportDateStr = (String) item.get("report_date");
                
                // 转换日期格式
                LocalDate reportDate = LocalDate.parse(reportDateStr);

                // UPSERT
                jdbcTemplate.update(
                        "INSERT INTO stock_finance (code, report_date, roe, revenue_growth, profit_growth, debt_ratio, cash_flow, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                        "ON CONFLICT (code, report_date) DO UPDATE SET " +
                        "roe = EXCLUDED.roe, revenue_growth = EXCLUDED.revenue_growth, " +
                        "profit_growth = EXCLUDED.profit_growth, debt_ratio = EXCLUDED.debt_ratio, " +
                        "cash_flow = EXCLUDED.cash_flow, update_time = CURRENT_TIMESTAMP",
                        code, reportDate,
                        toDecimal(item.get("roe")),
                        toDecimal(item.get("revenue_growth")),
                        toDecimal(item.get("profit_growth")),
                        toDecimal(item.get("debt_ratio")),
                        toDecimal(item.get("cash_flow"))
                );
                count++;
            } catch (Exception e) {
                log.warn("保存财务数据失败: {}", item.get("code"), e);
            }
        }
        log.info("保存财务数据: {} 条", count);
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
