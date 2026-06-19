package com.quant.market.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;
import com.quant.market.config.QuantProperties;
import com.quant.market.datasource.tickflow.SymbolUtils;
import com.quant.market.datasource.tickflow.TickflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("tickflowProvider")
@RequiredArgsConstructor
public class TickflowDataProvider implements MarketDataProvider {

    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int INSTRUMENT_BATCH = 100;

    private final TickflowClient client;
    private final QuantProperties properties;

    @Override
    public List<StockInfo> fetchStockList() {
        try {
            String universeId = properties.getTickflow().getUniverseId();
            JsonNode response = client.get("/v1/universes/" + universeId);
            JsonNode symbols = response.path("data").path("symbols");
            if (!symbols.isArray() || symbols.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> symbolList = new ArrayList<>();
            for (JsonNode node : symbols) {
                symbolList.add(node.asText());
            }

            Map<String, JsonNode> metadata = fetchInstrumentMetadata(symbolList);
            List<StockInfo> result = new ArrayList<>(symbolList.size());
            for (String symbol : symbolList) {
                StockInfo info = new StockInfo();
                info.setCode(SymbolUtils.toLocalCode(symbol));
                info.setMarket(SymbolUtils.extractMarket(symbol));
                JsonNode meta = metadata.get(symbol);
                if (meta != null) {
                    info.setName(meta.path("name").asText(null));
                    JsonNode ext = meta.path("ext");
                    if (ext.hasNonNull("listing_date")) {
                        info.setListDate(LocalDate.parse(ext.path("listing_date").asText()));
                    }
                }
                result.add(info);
            }
            log.info("TickFlow fetched {} stocks from universe {}", result.size(), universeId);
            return result;
        } catch (Exception e) {
            log.error("TickFlow fetchStockList failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<StockDaily> fetchDailyBars(String code, LocalDate start, LocalDate end) {
        try {
            String symbol = SymbolUtils.toTickflowSymbol(code, null);
            Map<String, String> params = new HashMap<>();
            params.put("symbol", symbol);
            params.put("period", "1d");
            params.put("adjust", "forward");
            params.put("start_time", String.valueOf(start.atStartOfDay(CN_ZONE).toInstant().toEpochMilli()));
            params.put("end_time", String.valueOf(end.plusDays(1).atStartOfDay(CN_ZONE).toInstant().toEpochMilli() - 1));

            JsonNode response = client.get("/v1/klines", params);
            JsonNode klineData = response.path("data");
            return parseCompactKlines(code, klineData);
        } catch (Exception e) {
            log.error("TickFlow fetchDailyBars failed for {}", code, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<StockFinance> fetchFinance(String code) {
        try {
            String symbol = SymbolUtils.toTickflowSymbol(code, null);
            Map<String, String> params = Map.of("symbols", symbol, "latest", "true");

            JsonNode metricsResp = client.get("/v1/financials/metrics", params);
            JsonNode cashFlowResp = client.get("/v1/financials/cash-flow", params);

            JsonNode metricsArr = metricsResp.path("data").path(symbol);
            if (!metricsArr.isArray() || metricsArr.isEmpty()) {
                return Collections.emptyList();
            }
            JsonNode metrics = metricsArr.get(0);

            BigDecimal cashFlow = null;
            JsonNode cashArr = cashFlowResp.path("data").path(symbol);
            if (cashArr.isArray() && !cashArr.isEmpty()) {
                cashFlow = decimal(cashArr.get(0).path("net_operating_cash_flow"));
            }

            StockFinance finance = new StockFinance();
            finance.setCode(code);
            finance.setReportDate(LocalDate.parse(metrics.path("period_end").asText()));
            finance.setRoe(normalizeRatio(metrics.path("roe")));
            finance.setRevenueGrowth(normalizeRatio(metrics.path("revenue_yoy")));
            finance.setProfitGrowth(normalizeRatio(metrics.path("net_income_yoy")));
            finance.setDebtRatio(normalizeRatio(metrics.path("debt_to_asset_ratio")));
            finance.setCashFlow(cashFlow);
            return List.of(finance);
        } catch (Exception e) {
            log.error("TickFlow fetchFinance failed for {}", code, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> fetchIndustryDaily(LocalDate tradeDate) {
        // TickFlow 暂无行业板块 API，由 AKShare 容灾数据源处理
        log.debug("TickFlow has no industry API, returning empty for fallback");
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> fetchCapitalFlow(LocalDate tradeDate) {
        // TickFlow 暂无主力资金流 API，由 AKShare 容灾数据源处理
        log.debug("TickFlow has no capital flow API, returning empty for fallback");
        return Collections.emptyList();
    }

    private Map<String, JsonNode> fetchInstrumentMetadata(List<String> symbols) {
        Map<String, JsonNode> result = new HashMap<>();
        for (int i = 0; i < symbols.size(); i += INSTRUMENT_BATCH) {
            List<String> batch = symbols.subList(i, Math.min(i + INSTRUMENT_BATCH, symbols.size()));
            String joined = String.join(",", batch);
            try {
                JsonNode resp = client.get("/v1/instruments", Map.of("symbols", joined));
                for (JsonNode item : resp.path("data")) {
                    result.put(item.path("symbol").asText(), item);
                }
            } catch (Exception e) {
                log.warn("TickFlow instrument batch fetch failed at offset {}", i, e);
            }
        }
        return result;
    }

    private List<StockDaily> parseCompactKlines(String code, JsonNode klineData) {
        JsonNode timestamps = klineData.path("timestamp");
        if (!timestamps.isArray() || timestamps.isEmpty()) {
            return Collections.emptyList();
        }

        List<StockDaily> result = new ArrayList<>();
        int size = timestamps.size();
        for (int i = 0; i < size; i++) {
            LocalDate tradeDate = Instant.ofEpochMilli(timestamps.get(i).asLong())
                    .atZone(CN_ZONE).toLocalDate();

            StockDaily daily = new StockDaily();
            daily.setCode(code);
            daily.setTradeDate(tradeDate);
            daily.setOpen(decimalAt(klineData, "open", i));
            daily.setHigh(decimalAt(klineData, "high", i));
            daily.setLow(decimalAt(klineData, "low", i));
            daily.setClose(decimalAt(klineData, "close", i));
            daily.setVolume(klineData.path("volume").get(i).asLong());
            daily.setAmount(decimalAt(klineData, "amount", i));
            result.add(daily);
        }
        return result;
    }

    private BigDecimal decimalAt(JsonNode node, String field, int index) {
        JsonNode arr = node.path(field);
        if (!arr.isArray() || index >= arr.size() || arr.get(index).isNull()) {
            return null;
        }
        return BigDecimal.valueOf(arr.get(index).asDouble());
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble());
    }

    /** TickFlow 财务指标可能是小数(0.15)或百分比(15)，统一为小数 */
    private BigDecimal normalizeRatio(JsonNode node) {
        BigDecimal value = decimal(node);
        if (value == null) {
            return null;
        }
        if (value.abs().compareTo(BigDecimal.ONE) > 0) {
            return value.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        }
        return value;
    }
}
