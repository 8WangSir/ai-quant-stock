package com.quant.notify.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.common.constant.RedisKeys;
import com.quant.common.dto.RecommendStockDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<NotificationChannel> channels;

    public void notifyRecommendPool() {
        List<RecommendStockDTO> stocks = loadFromRedis();
        if (stocks.isEmpty()) {
            log.warn("No recommend pool in Redis");
            return;
        }
        for (NotificationChannel channel : channels) {
            channel.sendRecommendPool(stocks);
        }
    }

    public void notifyAlert(String title, String message) {
        for (NotificationChannel channel : channels) {
            channel.sendAlert(title, message);
        }
    }

    private List<RecommendStockDTO> loadFromRedis() {
        try {
            String json = redisTemplate.opsForValue().get(RedisKeys.RECOMMEND_TODAY);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to load recommend pool from Redis", e);
            return Collections.emptyList();
        }
    }
}
