package com.quant.common.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class IndustryRankDTO {
    private String industryName;
    private LocalDate tradeDate;
    private BigDecimal strength;
    private Integer rank;
    private Integer score;
    private BigDecimal return20d;
    private BigDecimal return60d;
    private BigDecimal return120d;
}
