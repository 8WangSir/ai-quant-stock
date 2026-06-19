package com.quant.factor.job;

import com.quant.factor.service.FactorCalculationService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class FactorJobHandler {

    private final FactorCalculationService factorCalculationService;

    @XxlJob("calcIndicatorsJob")
    public void calcIndicators() {
        factorCalculationService.calculateIndicators(LocalDate.now());
    }

    @XxlJob("calcIndustryStrengthJob")
    public void calcIndustryStrength() {
        factorCalculationService.calculateIndustryStrength(LocalDate.now());
    }

    @XxlJob("calcScoresJob")
    public void calcScores() {
        factorCalculationService.calculateScores(LocalDate.now());
    }
}
