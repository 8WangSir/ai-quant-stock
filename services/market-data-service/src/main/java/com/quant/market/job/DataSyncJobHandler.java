package com.quant.market.job;

import com.quant.market.service.DataSyncService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 数据同步任务调度器
 *
 * 任务说明：
 * - initFullSyncJob: 首次全量同步（股票列表 + 近一年日线 + 最新财务）
 * - syncStockDailyIncrJob: 每日增量日线（只拉当天）
 * - syncFinanceFullJob: 财务全量同步（断点续传）
 * - syncFinanceIncrJob: 财务增量同步（指定季度）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncJobHandler {

    private final DataSyncService dataSyncService;

    // ==================== 全量同步（首次执行） ====================

    @XxlJob("initFullSyncJob")
    public void initFullSync() {
        LocalDate today = LocalDate.now();
        log.info("【全量同步】开始: {}", today);

        // 1. 股票列表
        try {
            dataSyncService.syncStockList();
        } catch (Exception e) {
            log.warn("股票列表同步失败（使用已有数据）: {}", e.getMessage());
        }

        // 2. 日线全量：近一年
        LocalDate start = today.minusYears(1);
        dataSyncService.syncDailyDataFull(start, today);

        // 3. 财务全量
        dataSyncService.syncFinanceDataFull();

        log.info("【全量同步】完成");
    }

    // ==================== 增量同步（每日执行） ====================

    @XxlJob("syncStockDailyIncrJob")
    public void syncStockDailyIncr() {
        LocalDate today = LocalDate.now();
        log.info("【增量日线】开始: {}", today);
        dataSyncService.syncDailyDataIncr(today);
        log.info("【增量日线】完成");
    }

    @XxlJob("syncFinanceIncrJob")
    public void syncFinanceIncr() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int quarter = (today.getMonthValue() - 1) / 3 + 1;
        log.info("【增量财务】开始: {}年Q{}", year, quarter);
        dataSyncService.syncFinanceDataIncr(year, quarter);
        log.info("【增量财务】完成");
    }

    // ==================== 手动触发（全量补录） ====================

    @XxlJob("syncFinanceFullJob")
    public void syncFinanceFull() {
        log.info("【财务全量】开始");
        dataSyncService.syncFinanceDataFull();
        log.info("【财务全量】完成");
    }

    @XxlJob("syncIndustryJob")
    public void syncIndustry() {
        LocalDate today = LocalDate.now();
        log.info("【行业】开始: {}", today);
        dataSyncService.syncIndustryData(today);
        log.info("【行业】完成");
    }

    @XxlJob("syncCapitalFlowJob")
    public void syncCapitalFlow() {
        LocalDate today = LocalDate.now();
        log.info("【资金流】开始: {}", today);
        dataSyncService.syncCapitalFlow(today);
        log.info("【资金流】完成");
    }
}
