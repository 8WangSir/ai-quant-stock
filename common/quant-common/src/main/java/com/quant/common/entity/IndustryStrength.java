package com.quant.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("industry_strength")
public class IndustryStrength {
    private String industryName;
    private LocalDate tradeDate;
    private BigDecimal return20d;
    private BigDecimal return60d;
    private BigDecimal return120d;
    private BigDecimal strength;
    private Integer rank;
    private Integer score;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
