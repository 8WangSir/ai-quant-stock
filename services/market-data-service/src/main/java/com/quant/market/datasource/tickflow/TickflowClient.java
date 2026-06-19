package com.quant.market.datasource.tickflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.quant.market.config.QuantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class TickflowClient {

    private final QuantProperties properties;
    private final RestClient restClient = RestClient.create();

    /** 限流：每分钟最多 8 次请求（TickFlow 免费额度 10/min，留余量） */
    private static final int MAX_REQUESTS_PER_MINUTE = 8;
    private static final long MIN_INTERVAL_MS = 60_000L / MAX_REQUESTS_PER_MINUTE;

    private final ReentrantLock rateLimitLock = new ReentrantLock();
    private long lastRequestTime = 0;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private long windowStart = System.currentTimeMillis();

    public JsonNode get(String path) {
        return get(path, Map.of());
    }

    public JsonNode get(String path, Map<String, String> queryParams) {
        String url = buildUrl(path, queryParams);
        return executeWithRetry(url, () -> doGET(url));
    }

    public JsonNode post(String path, Object body) {
        String url = buildUrl(path, null);
        return executeWithRetry(url, () -> doPOST(url, body));
    }

    /**
     * 带限流和 429 重试的执行逻辑
     */
    private JsonNode executeWithRetry(String url, RequestExecutor executor) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            waitForRateLimit();
            try {
                return executor.execute();
            } catch (HttpClientErrorException.TooManyRequests e) {
                long retryAfter = parseRetryAfter(e.getResponseBodyAsString(), attempt + 1);
                log.warn("TickFlow 429 限流，{}ms 后重试 (第{}次) url={}", retryAfter, attempt + 1, url);
                try {
                    Thread.sleep(retryAfter);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("限流等待被中断", ie);
                }
            } catch (HttpClientErrorException.Unauthorized e) {
                log.error("TickFlow 401 认证失败，请检查 API Key: {}", e.getResponseBodyAsString());
                throw e;
            } catch (HttpClientErrorException e) {
                log.error("TickFlow HTTP {} 错误 url={}: {}", e.getStatusCode(), url, e.getResponseBodyAsString());
                throw e;
            }
        }
        throw new RuntimeException("TickFlow 请求超过最大重试次数: " + url);
    }

    private JsonNode doGET(String url) {
        return restClient.get()
                .uri(url)
                .header("x-api-key", properties.getTickflow().getApiKey())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode doPOST(String url, Object body) {
        return restClient.post()
                .uri(url)
                .header("x-api-key", properties.getTickflow().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * 限流等待：保证每分钟不超过 MAX_REQUESTS_PER_MINUTE 次请求
     */
    private void waitForRateLimit() {
        rateLimitLock.lock();
        try {
            long now = System.currentTimeMillis();
            // 滑动窗口：如果超过1分钟，重置计数
            if (now - windowStart >= 60_000L) {
                windowStart = now;
                requestCount.set(0);
            }
            // 如果已达上限，等待到窗口结束
            if (requestCount.get() >= MAX_REQUESTS_PER_MINUTE) {
                long waitMs = 60_000L - (now - windowStart) + 100;
                log.info("TickFlow 限流等待 {}ms（已达 {}/min）", waitMs, MAX_REQUESTS_PER_MINUTE);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                windowStart = System.currentTimeMillis();
                requestCount.set(0);
            }
            // 保证两次请求之间有最小间隔
            long elapsed = System.currentTimeMillis() - lastRequestTime;
            if (elapsed < MIN_INTERVAL_MS) {
                try {
                    Thread.sleep(MIN_INTERVAL_MS - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestTime = System.currentTimeMillis();
            requestCount.incrementAndGet();
        } finally {
            rateLimitLock.unlock();
        }
    }

    /**
     * 从 429 响应中解析重试等待时间
     */
    private long parseRetryAfter(String body, int attempt) {
        try {
            // TickFlow 返回格式: {"code":"RATE_LIMITED","message":"K线查询限流 (10/min)，请 39351ms 后重试"}
            if (body != null && body.contains("请") && body.contains("ms")) {
                int start = body.indexOf("请");
                int end = body.indexOf("ms");
                if (start > 0 && end > start) {
                    String numStr = body.substring(start + 1, end).trim();
                    long ms = Long.parseLong(numStr);
                    // 指数退避 + 服务端建议时间
                    return ms + (long) Math.pow(2, attempt) * 1000;
                }
            }
        } catch (Exception ignored) {
        }
        // 默认：指数退避
        return (long) Math.pow(2, attempt) * 5000;
    }

    private String buildUrl(String path, Map<String, String> queryParams) {
        String baseUrl = properties.getTickflow().getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    url.append(key).append("=").append(value).append("&");
                }
            });
            if (url.charAt(url.length() - 1) == '&') {
                url.deleteCharAt(url.length() - 1);
            }
        }
        return url.toString();
    }

    @FunctionalInterface
    private interface RequestExecutor {
        JsonNode execute();
    }
}
