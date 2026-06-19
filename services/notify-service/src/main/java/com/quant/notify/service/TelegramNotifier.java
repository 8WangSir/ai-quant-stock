package com.quant.notify.service;

import com.quant.common.dto.RecommendStockDTO;
import com.quant.notify.config.NotifyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotifier implements NotificationChannel {

    private final NotifyProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public void sendRecommendPool(List<RecommendStockDTO> stocks) {
        if (!properties.getTelegram().isEnabled()) {
            return;
        }
        String text = "今日推荐股票池:\n" + stocks.stream()
                .map(s -> String.format("%d. %s(%s) 评分:%d", s.getRank(), s.getName(), s.getCode(), s.getTotalScore()))
                .collect(Collectors.joining("\n"));
        send(text);
    }

    @Override
    public void sendAlert(String title, String message) {
        if (!properties.getTelegram().isEnabled()) {
            return;
        }
        send(title + "\n" + message);
    }

    private void send(String text) {
        try {
            String url = "https://api.telegram.org/bot" + properties.getTelegram().getBotToken()
                    + "/sendMessage?chat_id=" + properties.getTelegram().getChatId()
                    + "&text=" + text;
            restClient.post().uri(url).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.error("Telegram notification failed", e);
        }
    }
}
