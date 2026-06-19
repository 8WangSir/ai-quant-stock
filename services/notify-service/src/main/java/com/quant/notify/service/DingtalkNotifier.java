package com.quant.notify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.common.dto.RecommendStockDTO;
import com.quant.notify.config.NotifyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DingtalkNotifier implements NotificationChannel {

    private final NotifyProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    @Override
    public void sendRecommendPool(List<RecommendStockDTO> stocks) {
        if (!properties.getDingtalk().isEnabled()) {
            return;
        }
        String text = "今日推荐股票池:\n" + stocks.stream()
                .map(s -> String.format("%d. %s(%s) 评分:%d", s.getRank(), s.getName(), s.getCode(), s.getTotalScore()))
                .collect(Collectors.joining("\n"));
        sendMarkdown("量化选股推荐", text);
    }

    @Override
    public void sendAlert(String title, String message) {
        if (!properties.getDingtalk().isEnabled()) {
            return;
        }
        sendMarkdown(title, message);
    }

    private void sendMarkdown(String title, String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            Map<String, String> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", text);
            body.put("markdown", markdown);

            restClient.post()
                    .uri(properties.getDingtalk().getWebhook())
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Dingtalk notification failed", e);
        }
    }
}
