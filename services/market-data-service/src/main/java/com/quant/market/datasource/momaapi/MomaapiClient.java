package com.quant.market.datasource.momaapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MomaAPI 客户端
 * Docker 容器内可用，有每日请求限额，需要限流控制
 */
@Slf4j
@Component
public class MomaapiClient {

    private static final String BASE_URL = "http://api.momaapi.com";

    /** 限流：每分钟最多 6 次请求（MomaAPI 有每日限额，保守控制） */
    private static final int MAX_REQUESTS_PER_MINUTE = 6;
    private static final long MIN_INTERVAL_MS = 60_000L / MAX_REQUESTS_PER_MINUTE;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String token;

    private final ReentrantLock rateLimitLock = new ReentrantLock();
    private long lastRequestTime = 0;
    private int requestCount = 0;
    private long windowStart = System.currentTimeMillis();

    public MomaapiClient(com.quant.market.config.QuantProperties properties) {
        this.token = properties.getMomaapi().getToken();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public List<Map<String, String>> fetchStockList() {
        String url = BASE_URL + "/hslt/list/" + token;
        try {
            String body = doGet(url);
            return objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("MomaAPI 股票列表请求失败: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> fetchHistoryKline(String code, String level, String adjust) {
        String url = BASE_URL + "/hsstock/history/" + code + "/" + level + "/" + adjust + "/" + token;
        return doGetWithRetry(url, code);
    }

    public List<Map<String, Object>> fetchLatestKline(String code, String level, String adjust, int limit) {
        String url = BASE_URL + "/hsstock/latest/" + code + "/" + level + "/" + adjust + "/" + token + "?lt=" + limit;
        return doGetWithRetry(url, code);
    }

    private List<Map<String, Object>> doGetWithRetry(String url, String code) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            waitForRateLimit();
            try {
                String body = doGet(url);
                return objectMapper.readValue(body, new TypeReference<>() {});
            } catch (HttpClientErrorException.TooManyRequests e) {
                long waitMs = 60_000L + (long) Math.pow(2, attempt) * 5000;
                log.warn("MomaAPI 429 限流 code={}，等待{}ms后重试(第{}次)", code, waitMs, attempt + 1);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return List.of();
                }
                // 重置限流窗口
                rateLimitLock.lock();
                try {
                    windowStart = System.currentTimeMillis();
                    requestCount = 0;
                } finally {
                    rateLimitLock.unlock();
                }
            } catch (Exception e) {
                if (attempt == maxRetries - 1) {
                    log.error("MomaAPI 历史K线请求失败 code={}: {}", code, e.getMessage());
                }
            }
        }
        return List.of();
    }

    private String doGet(String url) {
        return restClient.get().uri(url).retrieve().body(String.class);
    }

    private void waitForRateLimit() {
        rateLimitLock.lock();
        try {
            long now = System.currentTimeMillis();
            if (now - windowStart >= 60_000L) {
                windowStart = now;
                requestCount = 0;
            }
            if (requestCount >= MAX_REQUESTS_PER_MINUTE) {
                long waitMs = 60_000L - (now - windowStart) + 100;
                log.info("MomaAPI 限流等待 {}ms", waitMs);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                windowStart = System.currentTimeMillis();
                requestCount = 0;
            }
            long elapsed = System.currentTimeMillis() - lastRequestTime;
            if (elapsed < MIN_INTERVAL_MS) {
                try {
                    Thread.sleep(MIN_INTERVAL_MS - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestTime = System.currentTimeMillis();
            requestCount++;
        } finally {
            rateLimitLock.unlock();
        }
    }
}
