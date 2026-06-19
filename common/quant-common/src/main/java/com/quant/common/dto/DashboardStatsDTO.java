package com.quant.common.dto;

import lombok.Data;

@Data
public class DashboardStatsDTO {
    private Integer recommendCount;
    private Integer scoreCount;
    private Integer industryCount;
    private Integer sellSignalCount;
    private String latestTradeDate;
}
