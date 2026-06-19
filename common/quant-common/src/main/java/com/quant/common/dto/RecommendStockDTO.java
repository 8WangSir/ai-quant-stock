package com.quant.common.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecommendStockDTO {
    private String code;
    private String name;
    private LocalDate tradeDate;
    private Integer totalScore;
    private Integer rank;
    private String industry;
    private BigDecimal close;

    // 新增字段
    /** 当日涨跌幅 (%) */
    private BigDecimal changePercent;
    /** 现价 */
    private BigDecimal currentPrice;
    /** 目标价 (基于技术指标预测) */
    private BigDecimal targetPrice;
    /** 推荐持有天数 */
    private Integer holdDays;
    /** 建议买入时间 */
    private String buyTime;
    /** 建议卖出时间 */
    private String sellTime;
    /** 策略说明 */
    private String strategyDesc;
}
