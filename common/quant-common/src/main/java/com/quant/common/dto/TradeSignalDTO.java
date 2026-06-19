package com.quant.common.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TradeSignalDTO {
    private String code;
    private String name;
    private LocalDate tradeDate;
    private String signalType;
    private String reason;
}
