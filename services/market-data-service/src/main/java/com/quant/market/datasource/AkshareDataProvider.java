package com.quant.market.datasource;

import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AKShare 容灾数据源 - 通过 Python 脚本桥接调用
 */
@Slf4j
@Component("akshareProvider")
public class AkshareDataProvider implements MarketDataProvider {

    private final PythonBridge pythonBridge;

    public AkshareDataProvider(PythonBridge pythonBridge) {
        this.pythonBridge = pythonBridge;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<StockInfo> fetchStockList() {
        try {
            List<Map<String, Object>> data = pythonBridge.runScript("data_sync/akshare_client.py", "stock_list");
            List<StockInfo> result = new ArrayList<>();
            for (Map<String, Object> row : data) {
                StockInfo info = new StockInfo();
                info.setCode(String.valueOf(row.get("code")));
                info.setName(String.valueOf(row.get("name")));
                info.setMarket(String.valueOf(row.getOrDefault("market", "A")));
                info.setIndustry((String) row.get("industry"));
                result.add(info);
            }
            return result;
        } catch (Exception e) {
            log.error("AKShare fetchStockList failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<StockDaily> fetchDailyBars(String code, LocalDate start, LocalDate end) {
        try {
            List<Map<String, Object>> data = pythonBridge.runScript(
                    "data_sync/akshare_client.py", "daily", code, start.toString(), end.toString());
            List<StockDaily> result = new ArrayList<>();
            for (Map<String, Object> row : data) {
                StockDaily daily = new StockDaily();
                daily.setCode(code);
                daily.setTradeDate(LocalDate.parse(String.valueOf(row.get("trade_date"))));
                daily.setOpen(toDecimal(row.get("open")));
                daily.setHigh(toDecimal(row.get("high")));
                daily.setLow(toDecimal(row.get("low")));
                daily.setClose(toDecimal(row.get("close")));
                daily.setVolume(((Number) row.get("volume")).longValue());
                daily.setAmount(toDecimal(row.get("amount")));
                daily.setTurnoverRate(toDecimal(row.get("turnover_rate")));
                result.add(daily);
            }
            return result;
        } catch (Exception e) {
            log.error("AKShare fetchDailyBars failed for {}", code, e);
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<StockFinance> fetchFinance(String code) {
        try {
            List<Map<String, Object>> data = pythonBridge.runScript(
                    "data_sync/akshare_client.py", "finance", code);
            List<StockFinance> result = new ArrayList<>();
            for (Map<String, Object> row : data) {
                StockFinance finance = new StockFinance();
                finance.setCode(code);
                finance.setReportDate(LocalDate.parse(String.valueOf(row.get("report_date"))));
                finance.setRoe(toDecimal(row.get("roe")));
                finance.setRevenueGrowth(toDecimal(row.get("revenue_growth")));
                finance.setProfitGrowth(toDecimal(row.get("profit_growth")));
                finance.setDebtRatio(toDecimal(row.get("debt_ratio")));
                finance.setCashFlow(toDecimal(row.get("cash_flow")));
                result.add(finance);
            }
            return result;
        } catch (Exception e) {
            log.error("AKShare fetchFinance failed for {}", code, e);
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchIndustryDaily(LocalDate tradeDate) {
        try {
            return pythonBridge.runScript("data_sync/akshare_client.py", "industry", tradeDate.toString());
        } catch (Exception e) {
            log.error("AKShare fetchIndustryDaily failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchCapitalFlow(LocalDate tradeDate) {
        try {
            return pythonBridge.runScript("data_sync/akshare_client.py", "capital_flow", tradeDate.toString());
        } catch (Exception e) {
            log.error("AKShare fetchCapitalFlow failed", e);
            return Collections.emptyList();
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
