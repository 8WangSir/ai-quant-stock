package com.quant.market.datasource.infoway;

import com.fasterxml.jackson.databind.JsonNode;
import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;
import com.quant.market.datasource.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Infoway 数据源（主力）
 * Docker 容器内可用，86400次/天，批量查询100只/批
 * 5000只股票近一年日线：约50批×500根 = 25000条，预估30秒完成
 */
@Slf4j
@Component("infowayProvider")
@RequiredArgsConstructor
public class InfowayProvider implements MarketDataProvider {

    private final InfowayClient client;
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    /** 日K类型=8，单产品最大500根（覆盖约2年交易日） */
    private static final int KLINE_TYPE_DAILY = 8;
    private static final int MAX_KLINE_NUM = 500;
    private static final int BATCH_SIZE = 100;

    @Override
    public List<StockInfo> fetchStockList() {
        // Infoway 暂无股票列表接口，由其他数据源提供
        return Collections.emptyList();
    }

    @Override
    public List<StockDaily> fetchDailyBars(String code, LocalDate start, LocalDate end) {
        try {
            String infowayCode = toInfowayCode(code);
            JsonNode resp = client.fetchSingleKline(infowayCode, MAX_KLINE_NUM);
            if (resp == null || resp.path("ret").asInt() != 200) {
                return Collections.emptyList();
            }
            return parseKlineResponse(resp, code, start, end);
        } catch (Exception e) {
            log.error("Infoway 日线获取失败 {}: {}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 批量获取多只股票的日K线（供 DataSyncService 调用）
     * @param codes 股票代码列表（纯代码，如 000001）
     * @param start 开始日期
     * @param end 结束日期
     * @return 所有股票的日线数据
     */
    public List<StockDaily> fetchDailyBarsBatch(List<String> codes, LocalDate start, LocalDate end) {
        List<StockDaily> result = new ArrayList<>();
        for (int i = 0; i < codes.size(); i += BATCH_SIZE) {
            List<String> batch = codes.subList(i, Math.min(i + BATCH_SIZE, codes.size()));
            String infowayCodes = batch.stream()
                    .map(this::toInfowayCode)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            try {
                JsonNode resp = client.fetchBatchKline(infowayCodes, MAX_KLINE_NUM);
                if (resp != null && resp.path("ret").asInt() == 200) {
                    JsonNode data = resp.path("data");
                    if (data.isArray()) {
                        for (JsonNode stockNode : data) {
                            String symbol = stockNode.path("s").asText("");
                            String pureCode = fromInfowayCode(symbol);
                            if (pureCode == null) continue;
                            result.addAll(parseKlineResponse(stockNode, pureCode, start, end));
                        }
                    }
                }
                log.info("Infoway 批量K线: {}/{} (已获取{}条)", i + batch.size(), codes.size(), result.size());
            } catch (Exception e) {
                log.error("Infoway 批量K线失败 offset={}: {}", i, e.getMessage());
            }
        }
        return result;
    }

    @Override
    public List<StockFinance> fetchFinance(String code) {
        try {
            String symbol = toInfowayCode(code);
            StockFinance finance = new StockFinance();
            finance.setCode(code);

            // 1. statistics: ROE + debt_ratio
            JsonNode statsResp = client.fetchFinancialStatistics(symbol);
            if (statsResp != null && statsResp.path("ret").asInt() == 200) {
                JsonNode statsData = statsResp.path("data");
                if (statsData.isArray()) {
                    // 找最新一期
                    String latestPeriod = findLatestPeriod(statsData);
                    for (JsonNode item : statsData) {
                        if (!latestPeriod.equals(item.path("periodDate").asText(""))) continue;
                        String itemId = item.path("itemId").asText("");
                        switch (itemId) {
                            case "return_on_equity" ->
                                finance.setRoe(toDecimal(item.path("itemValue").asText(null)));
                            case "debt_to_asset" ->
                                finance.setDebtRatio(toDecimal(item.path("itemValue").asText(null)));
                        }
                    }
                }
            }

            // 2. income_statement: 计算营收增长率和净利润增长率（最近两期同比）
            JsonNode incomeResp = client.fetchIncomeStatement(symbol);
            if (incomeResp != null && incomeResp.path("ret").asInt() == 200) {
                JsonNode incomeData = incomeResp.path("data");
                if (incomeData.isArray() && incomeData.size() >= 2) {
                    // 按日期降序排列，取最新两期
                    java.util.TreeMap<String, JsonNode> periodMap = new java.util.TreeMap<>(java.util.Collections.reverseOrder());
                    for (JsonNode item : incomeData) {
                        String pDate = item.path("periodDate").asText("");
                        String itemId = item.path("itemId").asText("");
                        if ("total_revenue".equals(itemId) || "net_income".equals(itemId)) {
                            periodMap.computeIfAbsent(pDate, k -> item);
                        }
                    }
                    // 取最近两个有 total_revenue 的季度
                    List<JsonNode> revenueItems = filterByItemId(incomeData, "total_revenue", 2);
                    if (revenueItems.size() == 2) {
                        BigDecimal curr = toDecimal(revenueItems.get(0).path("itemValue").asText(null));
                        BigDecimal prev = toDecimal(revenueItems.get(1).path("itemValue").asText(null));
                        if (curr != null && prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
                            finance.setRevenueGrowth(curr.subtract(prev).divide(prev, 6, java.math.RoundingMode.HALF_UP));
                        }
                    }
                    List<JsonNode> profitItems = filterByItemId(incomeData, "net_income", 2);
                    if (profitItems.size() == 2) {
                        BigDecimal curr = toDecimal(profitItems.get(0).path("itemValue").asText(null));
                        BigDecimal prev = toDecimal(profitItems.get(1).path("itemValue").asText(null));
                        if (curr != null && prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
                            finance.setProfitGrowth(curr.subtract(prev).divide(prev, 6, java.math.RoundingMode.HALF_UP));
                        }
                    }
                    // 取最新报告期
                    if (!revenueItems.isEmpty()) {
                        String pDate = revenueItems.get(0).path("periodDate").asText("");
                        if (pDate != null && !pDate.isBlank()) {
                            finance.setReportDate(LocalDate.parse(pDate));
                        }
                    }
                }
            }

            // 至少有一个字段有值才返回
            if (finance.getRoe() != null || finance.getRevenueGrowth() != null
                    || finance.getProfitGrowth() != null || finance.getDebtRatio() != null
                    || finance.getCashFlow() != null) {
                if (finance.getReportDate() == null) {
                    finance.setReportDate(LocalDate.now());
                }
                return List.of(finance);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Infoway 财务数据获取失败 {}: {}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> fetchIndustryDaily(LocalDate tradeDate) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> fetchCapitalFlow(LocalDate tradeDate) {
        return Collections.emptyList();
    }

    private List<StockDaily> parseKlineResponse(JsonNode stockNode, String code, LocalDate start, LocalDate end) {
        List<StockDaily> result = new ArrayList<>();
        JsonNode respList = stockNode.path("respList");
        if (!respList.isArray()) return result;

        for (JsonNode bar : respList) {
            LocalDate tradeDate;
            try {
                long timestamp = bar.path("t").asLong();
                tradeDate = Instant.ofEpochSecond(timestamp).atZone(CN_ZONE).toLocalDate();
            } catch (Exception e) {
                continue;
            }

            if (tradeDate.isBefore(start) || tradeDate.isAfter(end)) continue;

            StockDaily daily = new StockDaily();
            daily.setCode(code);
            daily.setTradeDate(tradeDate);
            daily.setOpen(toDecimal(bar.path("o").asText(null)));
            daily.setHigh(toDecimal(bar.path("h").asText(null)));
            daily.setLow(toDecimal(bar.path("l").asText(null)));
            daily.setClose(toDecimal(bar.path("c").asText(null)));
            daily.setVolume(toLong(bar.path("v").asText(null)));
            daily.setAmount(toDecimal(bar.path("vw").asText(null)));
            result.add(daily);
        }
        return result;
    }

    /**
     * 纯代码 → Infoway 格式: 000001 → 000001.SZ, 600000 → 600000.SH
     */
    private String toInfowayCode(String code) {
        if (code.startsWith("6") || code.startsWith("9")) {
            return code + ".SH";
        } else {
            return code + ".SZ";
        }
    }

    /**
     * 从数据数组中找最新一期报告日期
     */
    private String findLatestPeriod(JsonNode dataArray) {
        String latest = "";
        for (JsonNode item : dataArray) {
            String pDate = item.path("periodDate").asText("");
            if (pDate.compareTo(latest) > 0) {
                latest = pDate;
            }
        }
        return latest;
    }

    /**
     * 按 itemId 过滤并按日期降序取前 N 条
     */
    private List<JsonNode> filterByItemId(JsonNode dataArray, String itemId, int limit) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode item : dataArray) {
            if (itemId.equals(item.path("itemId").asText(""))) {
                result.add(item);
            }
        }
        result.sort((a, b) -> b.path("periodDate").asText("").compareTo(a.path("periodDate").asText("")));
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    /**
     * Infoway 格式 → 纯代码: 000001.SZ → 000001
     */
    private String fromInfowayCode(String symbol) {
        int dot = symbol.indexOf('.');
        if (dot > 0) {
            return symbol.substring(0, dot);
        }
        return null;
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long toLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
