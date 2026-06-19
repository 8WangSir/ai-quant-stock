package com.quant.market.datasource.momaapi;

import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;
import com.quant.market.datasource.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MomaAPI 数据源（主力）
 * Docker 容器内可用，无限流
 * 5000只股票日线同步预估 8-40 分钟
 */
@Slf4j
@Component("momaapiProvider")
@RequiredArgsConstructor
public class MomaapiProvider implements MarketDataProvider {

    private final MomaapiClient client;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<StockInfo> fetchStockList() {
        List<Map<String, String>> stocks = client.fetchStockList();
        if (stocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<StockInfo> result = new ArrayList<>(stocks.size());
        for (Map<String, String> item : stocks) {
            String dm = item.get("dm"); // 000001.SZ
            if (dm == null || dm.isBlank()) continue;

            StockInfo info = new StockInfo();
            // 去掉后缀 .SZ/.SH，只保留纯代码
            info.setCode(dm.split("\\.")[0]);
            info.setName(item.get("mc"));
            String jys = item.get("jys");
            info.setMarket("SH".equalsIgnoreCase(jys) ? "SH" : "SZ");
            result.add(info);
        }

        log.info("MomaAPI 获取 {} 只股票", result.size());
        return result;
    }

    @Override
    public List<StockDaily> fetchDailyBars(String code, LocalDate start, LocalDate end) {
        try {
            String momaCode = toMomaCode(code);
            List<Map<String, Object>> klines = client.fetchHistoryKline(momaCode, "d", "f");
            if (klines.isEmpty()) {
                return Collections.emptyList();
            }

            List<StockDaily> result = new ArrayList<>();
            for (Map<String, Object> bar : klines) {
                String timeStr = bar.get("t").toString();
                LocalDate tradeDate;
                try {
                    tradeDate = LocalDateTime.parse(timeStr, DTF).toLocalDate();
                } catch (Exception e) {
                    continue;
                }

                if (tradeDate.isBefore(start) || tradeDate.isAfter(end)) {
                    continue;
                }

                StockDaily daily = new StockDaily();
                daily.setCode(code);
                daily.setTradeDate(tradeDate);
                daily.setOpen(toDecimal(bar.get("o")));
                daily.setHigh(toDecimal(bar.get("h")));
                daily.setLow(toDecimal(bar.get("l")));
                daily.setClose(toDecimal(bar.get("c")));
                daily.setVolume(toLong(bar.get("v")));
                daily.setAmount(toDecimal(bar.get("a")));
                result.add(daily);
            }

            return result;
        } catch (Exception e) {
            log.error("MomaAPI 日线获取失败 {}: {}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<StockFinance> fetchFinance(String code) {
        // MomaAPI 暂无财务数据接口
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> fetchIndustryDaily(LocalDate tradeDate) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> fetchCapitalFlow(LocalDate tradeDate) {
        return Collections.emptyList();
    }

    private String toMomaCode(String code) {
        if (code.startsWith("6") || code.startsWith("9")) {
            return code + ".SH";
        } else {
            return code + ".SZ";
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

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
