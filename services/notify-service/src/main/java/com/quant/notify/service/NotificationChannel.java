package com.quant.notify.service;

import com.quant.common.dto.RecommendStockDTO;

import java.util.List;

public interface NotificationChannel {
    void sendRecommendPool(List<RecommendStockDTO> stocks);
    void sendAlert(String title, String message);
}
