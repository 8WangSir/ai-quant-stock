package com.quant.market.datasource;

import com.quant.common.entity.StockDaily;
import com.quant.common.entity.StockFinance;
import com.quant.common.entity.StockInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Primary
public class CompositeMarketDataProvider implements MarketDataProvider {

    private final MarketDataProvider infoway;    // Infoway（主力，Docker可用，批量K线+财务）
    private final MarketDataProvider momaapi;    // MomaAPI（股票列表+K线备用）
    private final MarketDataProvider tickflow;   // TickFlow（K线+财务备用，有限流）
    private final MarketDataProvider juhe;       // 聚合数据（实时行情）
    private final MarketDataProvider eastmoney;  // 东方财富（Docker可能不可用）
    private final MarketDataProvider akshare;     // AKShare（最终兜底）

    public CompositeMarketDataProvider(
            @Qualifier("infowayProvider") MarketDataProvider infoway,
            @Qualifier("momaapiProvider") MarketDataProvider momaapi,
            @Qualifier("tickflowProvider") MarketDataProvider tickflow,
            @Qualifier("juheProvider") MarketDataProvider juhe,
            @Qualifier("eastmoneyProvider") MarketDataProvider eastmoney,
            @Qualifier("akshareProvider") MarketDataProvider akshare) {
        this.infoway = infoway;
        this.momaapi = momaapi;
        this.tickflow = tickflow;
        this.juhe = juhe;
        this.eastmoney = eastmoney;
        this.akshare = akshare;
    }

    @Override
    public List<StockInfo> fetchStockList() {
        return withFallback(
                momaapi::fetchStockList,
                tickflow::fetchStockList,
                eastmoney::fetchStockList,
                akshare::fetchStockList);
    }

    @Override
    public List<StockDaily> fetchDailyBars(String code, LocalDate start, LocalDate end) {
        return withFallback(
                () -> infoway.fetchDailyBars(code, start, end),
                () -> momaapi.fetchDailyBars(code, start, end),
                () -> tickflow.fetchDailyBars(code, start, end),
                () -> eastmoney.fetchDailyBars(code, start, end));
    }

    @Override
    public List<StockFinance> fetchFinance(String code) {
        // 财务数据只走 Infoway（Docker内唯一可用），不fallback避免卡住
        List<StockFinance> result = infoway.fetchFinance(code);
        return result != null ? result : List.of();
    }

    @Override
    public List<Map<String, Object>> fetchIndustryDaily(LocalDate tradeDate) {
        return withFallback(
                () -> eastmoney.fetchIndustryDaily(tradeDate),
                () -> akshare.fetchIndustryDaily(tradeDate),
                () -> tickflow.fetchIndustryDaily(tradeDate),
                () -> infoway.fetchIndustryDaily(tradeDate));
    }

    @Override
    public List<Map<String, Object>> fetchCapitalFlow(LocalDate tradeDate) {
        return withFallback(
                () -> eastmoney.fetchCapitalFlow(tradeDate),
                () -> akshare.fetchCapitalFlow(tradeDate),
                () -> tickflow.fetchCapitalFlow(tradeDate),
                () -> infoway.fetchCapitalFlow(tradeDate));
    }

    private <T> T withFallback(DataSupplier<T> a, DataSupplier<T> b, DataSupplier<T> c, DataSupplier<T> d) {
        T result = a.get();
        if (isNotEmpty(result)) return result;
        result = b.get();
        if (isNotEmpty(result)) return result;
        result = c.get();
        if (isNotEmpty(result)) return result;
        return d.get();
    }

    @SuppressWarnings("unchecked")
    private <T> boolean isNotEmpty(T result) {
        return result != null && result instanceof List<?> list && !list.isEmpty();
    }

    @FunctionalInterface
    private interface DataSupplier<T> {
        T get();
    }
}
