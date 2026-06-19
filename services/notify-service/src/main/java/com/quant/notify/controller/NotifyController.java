package com.quant.notify.controller;

import com.quant.common.dto.ApiResponse;
import com.quant.notify.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @PostMapping("/recommend")
    public ApiResponse<Void> notifyRecommend() {
        notifyService.notifyRecommendPool();
        return ApiResponse.success(null);
    }

    @PostMapping("/alert")
    public ApiResponse<Void> notifyAlert(
            @RequestParam String title,
            @RequestParam String message) {
        notifyService.notifyAlert(title, message);
        return ApiResponse.success(null);
    }
}
