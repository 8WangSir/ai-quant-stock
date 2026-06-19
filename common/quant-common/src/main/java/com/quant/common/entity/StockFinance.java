package com.quant.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_finance")
public class StockFinance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private LocalDate reportDate;
    private BigDecimal roe;
    private BigDecimal revenueGrowth;
    private BigDecimal profitGrowth;
    private BigDecimal debtRatio;
    private BigDecimal cashFlow;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
