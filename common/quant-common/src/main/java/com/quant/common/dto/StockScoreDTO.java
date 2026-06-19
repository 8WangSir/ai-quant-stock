package com.quant.common.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StockScoreDTO {
    private String code;
    private String name;
    private LocalDate tradeDate;
    private Integer financeScore;
    private Integer trendScore;
    private Integer capitalScore;
    private Integer industryScore;
    private Integer totalScore;
}
