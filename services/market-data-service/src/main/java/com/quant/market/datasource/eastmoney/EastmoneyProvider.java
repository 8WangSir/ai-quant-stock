package com.quant.market.datasource.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;
import com.quant.market.datasource.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 东方财富数据源（主力数据源）
 * 直接调用东方财富公开 HTTP API，无需 API Key，无限流
 * 性能：5000只股票日线约 10-15 分钟
 */
@Slf4j
@Component("eastmoneyProvider")
@RequiredArgsConstructor
public class EastmoneyProvider implements MarketDataProvider {

    private final EastmoneyClient client;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public List<StockInfo> fetchStockList() {
        List<StockInfo> result = new ArrayList<>();
        int pageSize = 5000;
        int page = 1;

        while (true) {
            JsonNode resp = client.fetchStockList(pageSize, page);
            if (resp == null || !resp.path("success").asBoolean(false)) {
                log.warn("东方财富股票列表请求失败 page={}", page);
                break;
            }

            JsonNode data = resp.path("result").path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }

            for (JsonNode item : data) {
                StockInfo info = new StockInfo();
                String code = item.path("SECURITY_CODE").asText("");
                if (code.isBlank()) continue;

                info.setCode(code);
                info.setName(item.path("SECURITY_NAME_ABBR").asText(null));

                // 判断市场：6开头=上海(1)，0/3开头=深圳(0)
                info.setCode(code);
                info.setMarket(code.startsWith("6") ? "SH" : "SZ");

                String listDateStr = item.path("LISTING_DATE").asText(null);
                if (listDateStr != null && !listDateStr.isBlank()) {
                    try {
                        info.setListDate(LocalDate.parse(listDateStr, DTF));
                    } catch (Exception ignored) {
                    }
                }
                result.add(info);
            }

            // 检查是否还有下一页
            int total = resp.path("result").path("count").asInt(0);
            if (result.size() >= total || data.size() < pageSize) {
                break;
            }
            page++;
        }

        log.info("东方财富获取 {} 只股票", result.size());
        return result;
    }

    @Override
    public List<StockDaily> fetchDailyBars(String code, LocalDate start, LocalDate end) {
        try {
            String secid = toSecid(code);
            // 东方财富 K线 API 一次最多返回 5000 条，足够覆盖历史数据
            JsonNode resp = client.fetchDailyKline(secid, 5000, 1);
            if (resp == null || !resp.path("success").asBoolean(false)) {
                return Collections.emptyList();
            }

            JsonNode klines = resp.path("data").path("klines");
            if (!klines.isArray() || klines.isEmpty()) {
                return Collections.emptyList();
            }

            List<StockDaily> result = new ArrayList<>();
            LocalDate filterStart = start;
            LocalDate filterEnd = end;

            for (JsonNode kline : klines) {
                String line = kline.asText("");
                String[] parts = line.split(",");
                if (parts.length < 7) continue;

                LocalDate tradeDate;
                try {
                    tradeDate = LocalDate.parse(parts[0], DTF);
                } catch (Exception e) {
                    continue;
                }

                // 过滤日期范围
                if (tradeDate.isBefore(filterStart) || tradeDate.isAfter(filterEnd)) {
                    continue;
                }

                StockDaily daily = new StockDaily();
                daily.setCode(code);
                daily.setTradeDate(tradeDate);
                daily.setOpen(toDecimal(parts[1]));
                daily.setClose(toDecimal(parts[2]));
                daily.setHigh(toDecimal(parts[3]));
                daily.setLow(toDecimal(parts[4]));
                daily.setVolume(toLong(parts[5]));
                daily.setAmount(toDecimal(parts[6]));
                // parts[7] = 振幅, parts[8] = 涨跌幅, parts[9] = 涨跌额, parts[10] = 换手率
                if (parts.length > 10) {
                    daily.setTurnoverRate(toDecimal(parts[10]));
                }
                result.add(daily);
            }

            return result;
        } catch (Exception e) {
            log.error("东方财富日线获取失败 {}: {}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<StockFinance> fetchFinance(String code) {
        try {
            JsonNode resp = client.fetchFinanceIndicator(code);
            if (resp == null || !resp.path("success").asBoolean(false)) {
                return Collections.emptyList();
            }

            JsonNode data = resp.path("result").path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Collections.emptyList();
            }

            JsonNode item = data.get(0);
            StockFinance finance = new StockFinance();
            finance.setCode(code);

            // ROE
            BigDecimal roe = toDecimal(item.path("WEIGHTAVG_ROE").asText(null));
            finance.setRoe(roe != null ? roe.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP) : null);

            // 营收增长率
            BigDecimal revGrowth = toDecimal(item.path("YSTZ").asText(null));
            finance.setRevenueGrowth(revGrowth != null ? revGrowth.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP) : null);

            // 净利润增长率
            BigDecimal profitGrowth = toDecimal(item.path("SJLTZ").asText(null));
            finance.setProfitGrowth(profitGrowth != null ? profitGrowth.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP) : null);

            // 资产负债率
            BigDecimal debtRatio = toDecimal(item.path("DEBT_ASSET_RATIO").asText(null));
            finance.setDebtRatio(debtRatio != null ? debtRatio.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP) : null);

            // 经营现金流
            finance.setCashFlow(toDecimal(item.path("OPERATE_CASH_FLOW").asText(null)));

            // 报告日期取当前日期（API 返回的是报告期，这里简化处理）
            finance.setReportDate(LocalDate.now());

            return List.of(finance);
        } catch (Exception e) {
            log.error("东方财富财务数据获取失败 {}: {}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> fetchIndustryDaily(LocalDate tradeDate) {
        try {
            JsonNode resp = client.fetchIndustryList();
            if (resp == null || !resp.path("success").asBoolean(false)) {
                return Collections.emptyList();
            }

            JsonNode data = resp.path("result").path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode item : data) {
                Map<String, Object> row = new HashMap<>();
                row.put("industry_name", item.path("BOARD_NAME").asText(""));
                row.put("close", toDecimal(item.path("NEW_PRICE").asText(null)));
                row.put("change_percent", toDecimal(item.path("CHANGE_RATE").asText(null)));
                row.put("amount", toDecimal(item.path("DEAL_AMOUNT").asText(null)));
                row.put("capital_flow", toDecimal(item.path("MAIN_NET_INFLOW").asText(null)));
                result.add(row);
            }

            log.info("东方财富获取 {} 个行业板块", result.size());
            return result;
        } catch (Exception e) {
            log.error("东方财富行业数据获取失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> fetchCapitalFlow(LocalDate tradeDate) {
        // 资金流需要逐只查询，这里返回空，由专门的批量接口处理
        // 或者可以通过东方财富的个股资金流排名接口批量获取
        log.debug("资金流数据由 DataSyncService 专门处理");
        return Collections.emptyList();
    }

    /**
     * 将股票代码转换为东方财富 secid 格式
     * 上海: 1.600000  深圳: 0.000001
     */
    private String toSecid(String code) {
        if (code.startsWith("6") || code.startsWith("9")) {
            return "1." + code;
        } else {
            return "0." + code;
        }
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
