package com.quant.market.datasource.juhe;

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
import java.util.*;

/**
 * 聚合数据数据源
 * 提供实时行情数据，Docker 容器内可用
 * 注意：聚合数据只提供实时行情，不提供历史K线
 * 历史K线由 TickFlow 提供
 */
@Slf4j
@Component("juheProvider")
@RequiredArgsConstructor
public class JuheProvider implements MarketDataProvider {

    private final JuheClient client;

    @Override
    public List<StockInfo> fetchStockList() {
        // 聚合数据没有股票列表接口，由 TickFlow 提供
        return Collections.emptyList();
    }

    @Override
    public List<StockDaily> fetchDailyBars(String code, LocalDate start, LocalDate end) {
        // 聚合数据只有实时行情，没有历史K线
        // 但可以返回当天的日线数据
        try {
            String gid = toGid(code);
            JsonNode resp = client.fetchQuote(gid);
            if (resp == null || !"200".equals(resp.path("resultcode").asText())) {
                return Collections.emptyList();
            }

            JsonNode result = resp.path("result");
            if (!result.isArray() || result.isEmpty()) {
                return Collections.emptyList();
            }

            JsonNode data = result.get(0).path("data");
            String dateStr = data.path("date").asText("");
            LocalDate tradeDate;
            try {
                tradeDate = LocalDate.parse(dateStr);
            } catch (Exception e) {
                return Collections.emptyList();
            }

            // 只返回请求日期范围内的数据
            if (tradeDate.isBefore(start) || tradeDate.isAfter(end)) {
                return Collections.emptyList();
            }

            StockDaily daily = new StockDaily();
            daily.setCode(code);
            daily.setTradeDate(tradeDate);
            daily.setOpen(toDecimal(data.path("todayStartPri").asText(null)));
            daily.setClose(toDecimal(data.path("nowPri").asText(null)));
            daily.setHigh(toDecimal(data.path("todayMax").asText(null)));
            daily.setLow(toDecimal(data.path("todayMin").asText(null)));
            daily.setVolume(toLong(data.path("traNumber").asText(null)));
            daily.setAmount(toDecimal(data.path("traAmount").asText(null)));

            return List.of(daily);
        } catch (Exception e) {
            log.error("聚合数据日线获取失败 {}: {}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<StockFinance> fetchFinance(String code) {
        // 聚合数据没有财务数据接口
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> fetchIndustryDaily(LocalDate tradeDate) {
        // 聚合数据没有行业板块接口
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> fetchCapitalFlow(LocalDate tradeDate) {
        // 聚合数据没有资金流接口
        return Collections.emptyList();
    }

    /**
     * 将股票代码转换为聚合数据 gid 格式
     * 6开头=sh, 0/3开头=sz
     */
    private String toGid(String code) {
        if (code.startsWith("6") || code.startsWith("9")) {
            return "sh" + code;
        } else {
            return "sz" + code;
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
