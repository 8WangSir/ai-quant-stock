package com.quant.admin.controller;

import com.quant.common.dto.ApiResponse;
import com.quant.common.dto.DashboardStatsDTO;
import com.quant.common.dto.IndustryRankDTO;
import com.quant.common.dto.RecommendStockDTO;
import com.quant.common.dto.StockScoreDTO;
import com.quant.common.dto.TradeSignalDTO;
import com.quant.admin.service.AdminQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminController {

    private final AdminQueryService adminQueryService;

    @GetMapping("/recommend/list")
    public ApiResponse<List<RecommendStockDTO>> recommendList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(adminQueryService.getRecommendList(date));
    }

    @GetMapping("/stock/score/{code}")
    public ApiResponse<StockScoreDTO> stockScore(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        StockScoreDTO score = adminQueryService.getStockScore(code, date);
        if (score == null) {
            return ApiResponse.error("未找到评分数据");
        }
        return ApiResponse.success(score);
    }

    @GetMapping("/industry/rank")
    public ApiResponse<List<IndustryRankDTO>> industryRank(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(adminQueryService.getIndustryRank(date));
    }

    @GetMapping("/signal/{code}")
    public ApiResponse<List<TradeSignalDTO>> signals(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(adminQueryService.getSignals(code, date));
    }

    @GetMapping("/score/list")
    public ApiResponse<List<StockScoreDTO>> scoreList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "0") Integer minScore) {
        return ApiResponse.success(adminQueryService.getScoreList(date, minScore));
    }

    @GetMapping("/signal/list")
    public ApiResponse<List<TradeSignalDTO>> sellSignalList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(adminQueryService.getAllSellSignals(date));
    }

    @GetMapping("/dashboard/stats")
    public ApiResponse<DashboardStatsDTO> dashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(adminQueryService.getDashboardStats(date));
    }
}
