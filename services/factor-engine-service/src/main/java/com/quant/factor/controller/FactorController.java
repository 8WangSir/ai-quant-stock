package com.quant.factor.controller;

import com.quant.common.dto.ApiResponse;
import com.quant.factor.service.FactorCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/factor")
@RequiredArgsConstructor
public class FactorController {

    private final FactorCalculationService factorCalculationService;

    @PostMapping("/calculate/indicators")
    public ApiResponse<String> calculateIndicators(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
        log.info("【指标计算】开始计算技术指标: {}", date);
        new Thread(() -> {
            factorCalculationService.calculateIndicators(date);
        }).start();
        return ApiResponse.success("技术指标计算已启动: " + date);
    }

    @PostMapping("/calculate/industry")
    public ApiResponse<String> calculateIndustryStrength(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
        log.info("【指标计算】开始计算行业强度: {}", date);
        new Thread(() -> {
            factorCalculationService.calculateIndustryStrength(date);
        }).start();
        return ApiResponse.success("行业强度计算已启动: " + date);
    }

    @PostMapping("/calculate/scores")
    public ApiResponse<String> calculateScores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
        log.info("【指标计算】开始计算评分: {}", date);
        new Thread(() -> {
            factorCalculationService.calculateScores(date);
        }).start();
        return ApiResponse.success("评分计算已启动: " + date);
    }

    @PostMapping("/calculate/all")
    public ApiResponse<String> calculateAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
        log.info("【指标计算】开始计算所有指标: {}", date);
        new Thread(() -> {
            factorCalculationService.calculateIndicators(date);
            factorCalculationService.calculateIndustryStrength(date);
            factorCalculationService.calculateScores(date);
            factorCalculationService.generateRecommendPool(date);
            log.info("【指标计算】所有指标计算完成: {}", date);
        }).start();
        return ApiResponse.success("所有指标计算已启动: " + date);
    }

    @PostMapping("/calculate/recommend")
    public ApiResponse<String> generateRecommendPool(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate date = tradeDate != null ? tradeDate : LocalDate.now();
        log.info("【指标计算】开始生成推荐池: {}", date);
        new Thread(() -> {
            factorCalculationService.generateRecommendPool(date);
        }).start();
        return ApiResponse.success("推荐池生成已启动: " + date);
    }
}