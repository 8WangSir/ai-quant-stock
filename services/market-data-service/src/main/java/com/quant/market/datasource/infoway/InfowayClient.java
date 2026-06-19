package com.quant.market.datasource.infoway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Infoway API 客户端
 * Docker 容器内可用，86400次/天，60次/分钟（1次/秒）
 * 支持批量K线查询 + 财务数据查询
 */
@Slf4j
@Component
public class InfowayClient {

    private static final String KLINE_URL = "https://data.infoway.io/stock/v2/batch_kline";
    private static final String FINANCE_URL = "https://data.infoway.io/common/basic/financial";
    private static final long MIN_INTERVAL_MS = 1050; // 1次/秒极限

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;

    private final ReentrantLock rateLimitLock = new ReentrantLock();
    private long lastRequestTime = 0;

    public InfowayClient(com.quant.market.config.QuantProperties properties) {
        this.apiKey = properties.getInfoway().getApiKey();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(60000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    // ==================== K线接口 ====================

    public JsonNode fetchBatchKline(String codes, int klineNum) {
        return fetchBatchKline(codes, klineNum, null);
    }

    public JsonNode fetchBatchKline(String codes, int klineNum, Long timestamp) {
        waitForRateLimit();

        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append("{\"klineType\":8,\"klineNum\":").append(klineNum);
        bodyBuilder.append(",\"codes\":\"").append(codes).append("\"");
        if (timestamp != null) {
            bodyBuilder.append(",\"timestamp\":").append(timestamp);
        }
        bodyBuilder.append("}");

        try {
            String body = restClient.post()
                    .uri(KLINE_URL)
                    .header("apiKey", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyBuilder.toString())
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Infoway 429 限流，等待5秒后重试");
            try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            return null;
        } catch (Exception e) {
            log.error("Infoway K线请求失败: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode fetchSingleKline(String code, int klineNum) {
        return fetchBatchKline(code, klineNum);
    }

    // ==================== 财务接口 ====================

    /**
     * 获取财务统计指标（ROE、资产负债率等）
     * 返回最新一期数据
     */
    public JsonNode fetchFinancialStatistics(String symbol) {
        String url = FINANCE_URL + "/statistics?symbol=" + symbol + "&type=STOCK_CN&period_type=fq";
        return doGet(url);
    }

    /**
     * 获取损益表（营收、净利润，用于计算同比增长率）
     */
    public JsonNode fetchIncomeStatement(String symbol) {
        String url = FINANCE_URL + "/income_statement?symbol=" + symbol + "&type=STOCK_CN&period_type=fq";
        return doGet(url);
    }

    /**
     * 获取现金流量表（经营现金流）
     */
    public JsonNode fetchCashFlow(String symbol) {
        String url = FINANCE_URL + "/cash_flow?symbol=" + symbol + "&type=STOCK_CN&period_type=fq";
        return doGet(url);
    }

    // ==================== 通用方法 ====================

    private JsonNode doGet(String url) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            waitForRateLimit();
            try {
                String body = restClient.get()
                        .uri(url)
                        .header("apiKey", apiKey)
                        .retrieve()
                        .body(String.class);
                return objectMapper.readTree(body);
            } catch (HttpClientErrorException.TooManyRequests e) {
                long waitMs = 60000; // 429后固定等60秒，让配额窗口刷新
                log.warn("Infoway 429 限流，等待{}ms后重试(第{}次)", waitMs, attempt + 1);
                try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            } catch (Exception e) {
                log.error("Infoway 请求失败 url={}: {}", url, e.getMessage());
                return null;
            }
        }
        return null;
    }

    private void waitForRateLimit() {
        rateLimitLock.lock();
        try {
            long elapsed = System.currentTimeMillis() - lastRequestTime;
            if (elapsed < MIN_INTERVAL_MS) {
                try {
                    Thread.sleep(MIN_INTERVAL_MS - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestTime = System.currentTimeMillis();
        } finally {
            rateLimitLock.unlock();
        }
    }
}
