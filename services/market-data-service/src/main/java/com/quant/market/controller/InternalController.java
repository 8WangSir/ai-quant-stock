package com.quant.market.controller;

import com.quant.market.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {

    private final DataSyncService dataSyncService;
    private final JdbcTemplate jdbcTemplate;


    //1 第一步 获取全量股票列表 系统执行一次即可
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

    //2 第二部 同步行业数据
    @PostMapping("/sync/industry")
    public String syncIndustry() {
        try {
            dataSyncService.syncIndustryData();
            return "行业数据同步已启动";
        } catch (Exception e) {
            log.error("行业数据同步异常", e);
            return "行业数据同步异常: " + e.getMessage();
        }
    }

    //3  第二部 同步行业每日行情数据
    @PostMapping("/sync/industry-daily")
    public String syncIndustryDaily(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusYears(1);
            LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
            new Thread(() -> dataSyncService.syncIndustryDaily(start, end)).start();
            return "行业行情同步已启动: " + start + " ~ " + end;
        } catch (Exception e) {
            log.error("行业行情同步异常", e);
            return "行业行情同步异常: " + e.getMessage();
        }
    }

    //4 第三步 同步所有股票列表日K线 (暂定近一年) 首次使用系统执行一次
    @PostMapping("/sync/finance-full")
    public String syncFinanceFull() {
        new Thread(() -> dataSyncService.syncFinanceDataFull()).start();
        return "财务全量同步已启动";
    }

    //2，2 第二步 增量同步所有股票列表日K线 (暂定近一年) 每日执行一次
    @PostMapping("/sync/daily-incr")
    public String syncDailyIncr() {
        new Thread(() -> dataSyncService.syncDailyDataIncr(LocalDate.now())).start();
        return "日线增量同步已启动";
    }


    // 3.1 使用 AKShare 批量同步财务数据（速度快）
    @PostMapping("/sync/finance-akshare")
    public String syncFinanceAkshare(@RequestParam(required = false) String date) {
        try {
            if (date == null || date.isEmpty()) {
                // 默认同步最近4个季度
                new Thread(() -> dataSyncService.syncFinanceAkshareRecent()).start();
                return "AKShare 财务数据同步已启动（最近4个季度）";
            } else {
                // 同步指定季度
                new Thread(() -> dataSyncService.syncFinanceAkshare(date)).start();
                return "AKShare 财务数据同步已启动: " + date;
            }
        } catch (Exception e) {
            log.error("AKShare 财务数据同步异常", e);
            return "AKShare 财务数据同步异常: " + e.getMessage();
        }
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

    // 同步日K增量数据（从指定日期开始同步到最新交易日）
    @PostMapping("/sync/dk")
    public String syncDailyIncr(@org.springframework.web.bind.annotation.RequestParam(required = false) String startDate) {
        new Thread(() -> {
            try {
                // 如果没有指定开始日期，从数据库中最后一个完整数据的日期开始
                LocalDate syncStart;
                if (startDate != null && !startDate.isEmpty()) {
                    syncStart = LocalDate.parse(startDate);
                } else {
                    // 查找最后一个数据量正常的日期（> 4000条）
                    String sql = "SELECT trade_date FROM stock_daily WHERE trade_date >= '2026-06-01' GROUP BY trade_date HAVING COUNT(*) > 4000 ORDER BY trade_date DESC LIMIT 1";
                    LocalDate lastCompleteDate = jdbcTemplate.queryForObject(sql, LocalDate.class);
                    if (lastCompleteDate == null) {
                        lastCompleteDate = LocalDate.of(2026, 6, 5);
                    }
                    syncStart = lastCompleteDate.plusDays(1);
                }
                
                LocalDate today = LocalDate.now();
                
                log.info("【日线增量同步】开始: 从{}到{}", syncStart, today);

                // 循环同步每一天的数据
                LocalDate currentDate = syncStart;
                int syncedDays = 0;
                while (!currentDate.isAfter(today)) {
                    // 跳过周末
                    if (currentDate.getDayOfWeek().getValue() <= 5) {
                        log.info("【日线增量同步】同步日期: {} ({})", currentDate, currentDate.getDayOfWeek().name());
                        dataSyncService.syncDailyDataIncr(currentDate);
                        syncedDays++;
                    }
                    currentDate = currentDate.plusDays(1);
                }

                log.info("【日线增量同步】完成，共同步 {} 个交易日", syncedDays);
            } catch (Exception e) {
                log.error("【日线增量同步】异常", e);
            }
        }).start();
        return "日线增量同步已启动";
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
