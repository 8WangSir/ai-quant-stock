package com.quant.market.datasource.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 东方财富 HTTP API 客户端
 * 直接调用东方财富公开 API，无需 API Key，无限流
 */
@Slf4j
@Component
public class EastmoneyClient {

    private static final String BASE_URL = "https://push2his.eastmoney.com";
    private static final String QUOTE_URL = "https://push2.eastmoney.com";
    private static final String API_URL = "https://datacenter-web.eastmoney.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER = "https://quote.eastmoney.com";

    private final RestClient restClient;

    public EastmoneyClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        factory.setBufferRequestBody(true);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    private JsonNode doGet(String url, String referer) {
        try {
            return restClient.get()
                    .uri(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "*/*")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("东方财富请求失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    public JsonNode fetchDailyKline(String secid, int pageSize, int pageIndex) {
        String url = BASE_URL + "/api/qt/stock/kline/get"
                + "?secid=" + secid
                + "&fields1=f1,f2,f3,f4,f5,f6"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                + "&klt=101&fqt=1&beg=0&end=20500101"
                + "&lmt=" + pageSize
                + "&pg=" + pageIndex;
        return doGet(url, REFERER);
    }

    public JsonNode fetchBatchQuote(List<String> secids) {
        String joined = String.join(",", secids);
        String url = QUOTE_URL + "/api/qt/stock/get"
                + "?secid=" + joined
                + "&fields=f43,f44,f45,f46,f47,f48,f50,f57,f58,f60,f116,f117,f162,f167,f168,f169,f170"
                + "&ut=fa5fd1943c7b386f172d6893dbfba10b";
        return doGet(url, REFERER);
    }

    public JsonNode fetchStockList(int pageSize, int pageIndex) {
        String url = API_URL + "/api/data/v1/get"
                + "?reportName=RPT_LICO_FN_CPD"
                + "&columns=SECURITY_CODE,SECURITY_NAME_ABBR,LISTING_DATE"
                + "&filter=(SECURITY_TYPE_CODE in (\"058001001\",\"058001008\"))"
                + "&pageSize=" + pageSize
                + "&pageNumber=" + pageIndex
                + "&sortTypes=1&sortColumns=SECURITY_CODE"
                + "&source=WEB&client=WEB";
        return doGet(url, "https://data.eastmoney.com");
    }

    public JsonNode fetchIndustryList() {
        String url = API_URL + "/api/data/v1/get"
                + "?reportName=RPT_BOARD_INDUSTRY_REALTIME"
                + "&columns=BOARD_CODE,BOARD_NAME,CHANGE_RATE,NEW_PRICE,TURNOVERRATE,DEAL_AMOUNT,MAIN_NET_INFLOW"
                + "&pageSize=500&pageNumber=1"
                + "&sortTypes=-1&sortColumns=CHANGE_RATE"
                + "&source=WEB&client=WEB";
        return doGet(url, "https://data.eastmoney.com");
    }

    public JsonNode fetchCapitalFlow(String secid) {
        String url = QUOTE_URL + "/api/qt/stock/fflow/daykline/get"
                + "?secid=" + secid
                + "&fields1=f1,f2,f3,f7"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64,f65"
                + "&lmt=1&klt=101&fqt=1"
                + "&ut=fa5fd1943c7b386f172d6893dbfba10b";
        return doGet(url, "https://data.eastmoney.com");
    }

    public JsonNode fetchFinanceIndicator(String secid) {
        String url = API_URL + "/api/data/v1/get"
                + "?reportName=RPT_F10_FINANCE_MAINFINADATA"
                + "&columns=TOTAL_OPERATE_INCOME,PARENT_NETPROFIT,WEIGHTAVG_ROE,BASIC_EPS,YSTZ,SJLTZ,DEBT_ASSET_RATIO,OPERATE_CASH_FLOW"
                + "&filter=(SECURITY_CODE=\"%s\")".formatted(secid)
                + "&pageSize=1&pageNumber=1"
                + "&sortTypes=-1&sortColumns=REPORT_DATE"
                + "&source=WEB&client=WEB";
        return doGet(url, "https://emweb.securities.eastmoney.com");
    }
}
