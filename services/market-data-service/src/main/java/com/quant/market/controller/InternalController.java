package com.quant.market.controller;

import com.quant.market.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {

    private final DataSyncService dataSyncService;

    @PostMapping("/sync/finance-full")
    public String syncFinanceFull() {
        new Thread(() -> dataSyncService.syncFinanceDataFull()).start();
        return "财务全量同步已启动";
    }

    @PostMapping("/sync/daily-incr")
    public String syncDailyIncr() {
        new Thread(() -> dataSyncService.syncDailyDataIncr(LocalDate.now())).start();
        return "日线增量同步已启动";
    }

    //同步股票列表
    @PostMapping("/sync/stock-list")
    public String syncStockList() {
        try {
            dataSyncService.syncStockList();
            return "股票列表同步已启动";
        } catch (Exception e) {
            log.error("股票列表同步异常", e);
            return "股票列表同步异常: " + e.getMessage();
        }
    }

    // 触发全量同步任务（对应 @XxlJob("initFullSyncJob")）
    @PostMapping("/sync/full")
    public String syncFull() {
        new Thread(() -> {
            try {
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
            } catch (Exception e) {
                log.error("【全量同步】异常", e);
            }
        }).start();
        return "全量同步已启动";
    }

    // 触发全量同步任务（支持断点续传）
    @PostMapping("/sync/full-resume")
    public String syncFullResume(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int startIndex) {
        new Thread(() -> {
            try {
                LocalDate today = LocalDate.now();
                log.info("【全量同步（断点续传）】开始: {}, 从第{}只股票开始", today, startIndex + 1);

                // 日线全量（断点续传）：近一年
                LocalDate start = today.minusYears(1);
                dataSyncService.syncDailyDataFull(start, today, startIndex);

                // 财务全量
                dataSyncService.syncFinanceDataFull();

                log.info("【全量同步（断点续传）】完成");
            } catch (Exception e) {
                log.error("【全量同步（断点续传）】异常", e);
            }
        }).start();
        return "全量同步（断点续传）已启动";
    }

    // 同步日K和财务数据
    @PostMapping("/sync/dk")
    public String syncFullResumeK(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int startIndex) {
        new Thread(() -> {
            try {
                LocalDate today = LocalDate.now();
                today = today.minusDays(1);
                log.info("【全量同步（断点续传）】开始: {}, 从第{}只股票开始", today, startIndex + 1);

                // 日线全量（断点续传）：近一年
                LocalDate start = today.minusYears(1);
                dataSyncService.syncDailyDataFull(start, today, startIndex);

                // 财务全量
                //dataSyncService.syncFinanceDataFull();

                log.info("【全量同步（断点续传）】完成");
            } catch (Exception e) {
                log.error("【全量同步（断点续传）】异常", e);
            }
        }).start();
        return "全量同步（断点续传）已启动";
    }

    @PostMapping("/sync/syncFullResumeF")
    public String syncFullResumeF() {
        new Thread(() -> {
            try {
                LocalDate today = LocalDate.now();
                log.info("【全量同步（断点续传）】开始: {}","同步财务数据" );

                // 财务全量
                dataSyncService.syncFinanceDataFull();

                log.info("【全量同步（断点续传）】完成");
            } catch (Exception e) {
                log.error("【全量同步（断点续传）】异常", e);
            }
        }).start();
        return "全量同步（断点续传）已启动";
    }
}
