package com.quant.strategy.job;

import com.quant.strategy.service.StrategyService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class StrategyJobHandler {

    private final StrategyService strategyService;

    @XxlJob("generateRecommendPoolJob")
    public void generateRecommendPool() {
        LocalDate today = LocalDate.now();
        strategyService.generateRecommendPool(today);
        strategyService.detectSellSignals(today);
    }
}
