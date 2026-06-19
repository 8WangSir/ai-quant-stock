package com.quant.market.datasource;

import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MarketDataProvider {

    List<StockInfo> fetchStockList();

    List<StockDaily> fetchDailyBars(String code, LocalDate start, LocalDate end);

    List<StockFinance> fetchFinance(String code);

    List<Map<String, Object>> fetchIndustryDaily(LocalDate tradeDate);

    List<Map<String, Object>> fetchCapitalFlow(LocalDate tradeDate);
}
