package com.quant.admin.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/api/logs")
public class LogStreamController {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 服务日志文件映射
    private final Map<String, String> serviceLogPaths = new LinkedHashMap<>();

    @Value("${log.base-dir:}")
    private String logBaseDir;

    @PostConstruct
    public void init() {
        if (logBaseDir != null && !logBaseDir.isEmpty()) {
            // Docker 环境：使用配置的基础目录
            serviceLogPaths.put("admin-service", logBaseDir + "/admin-service.log");
            serviceLogPaths.put("market-data-service", logBaseDir + "/market-data-service.log");
            serviceLogPaths.put("factor-engine-service", logBaseDir + "/factor-engine-service.log");
            serviceLogPaths.put("strategy-service", logBaseDir + "/strategy-service.log");
            serviceLogPaths.put("notify-service", logBaseDir + "/notify-service.log");
        } else {
            // 本地开发环境
            String baseDir = "c:\\Users\\wangw\\ai-quant-stock\\services\\";
            serviceLogPaths.put("admin-service", baseDir + "admin-service/logs/admin-service.log");
            serviceLogPaths.put("market-data-service", baseDir + "market-data-service/logs/market-data-service.log");
            serviceLogPaths.put("factor-engine-service", baseDir + "factor-engine-service/logs/factor-engine-service.log");
            serviceLogPaths.put("strategy-service", baseDir + "strategy-service/logs/strategy-service.log");
            serviceLogPaths.put("notify-service", baseDir + "notify-service/logs/notify-service.log");
        }
        log.info("Log paths configured: {}", serviceLogPaths);
    }

    /**
     * SSE 日志流端点
     * @param service 服务名（可选，不传则返回所有服务）
     * @param level 日志级别过滤（可选：INFO/ERROR/WARN）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level) {

        SseEmitter emitter = new SseEmitter(0L); // 永不超时

        executor.execute(() -> {
            try {
                List<String> services = service != null
                        ? Collections.singletonList(service)
                        : new ArrayList<>(serviceLogPaths.keySet());

                // 发送服务列表
                emitter.send(SseEmitter.event()
                        .name("services")
                        .data(new ArrayList<>(serviceLogPaths.keySet())));

                // 先发送历史日志（每个服务最近 50 条）
                for (String svc : services) {
                    List<LogEntry> history = getHistory(svc, 50, level);
                    for (LogEntry entry : history) {
                        emitter.send(SseEmitter.event()
                                .name("log")
                                .data(entry));
                    }
                }

                // 为每个服务启动日志 tail
                List<LogTailer> tailers = new ArrayList<>();
                for (String svc : services) {
                    String path = serviceLogPaths.get(svc);
                    if (path != null) {
                        LogTailer tailer = new LogTailer(svc, path, level, emitter);
                        tailers.add(tailer);
                        tailer.start();
                    }
                }

                // 保持连接，等待客户端断开
                while (true) {
                    try {
                        emitter.send(SseEmitter.event().name("ping").data(""));
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        break; // 连接断开
                    }
                }

                // 清理
                for (LogTailer tailer : tailers) {
                    tailer.stop();
                }
            } catch (Exception e) {
                log.error("Log stream error", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.info("Log stream completed"));
        emitter.onTimeout(() -> log.warn("Log stream timeout"));
        emitter.onError((e) -> log.error("Log stream error", e));

        return emitter;
    }

    /**
     * 获取历史日志（最近 N 行）
     */
    @GetMapping("/history")
    public List<LogEntry> getHistory(
            @RequestParam String service,
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(required = false) String level) {

        List<LogEntry> result = new ArrayList<>();
        String path = serviceLogPaths.get(service);
        if (path == null) return result;

        File file = new File(path);
        if (!file.exists()) return result;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long pos = raf.length();
            int count = 0;
            StringBuilder sb = new StringBuilder();

            while (pos > 0 && count < lines) {
                pos--;
                raf.seek(pos);
                int b = raf.read();
                if (b == '\n') {
                    String line = sb.reverse().toString();
                    if (!line.trim().isEmpty()) {
                        LogEntry entry = parseLogLine(service, line);
                        if (level == null || entry.getLevel().equals(level)) {
                            result.add(0, entry);
                            count++;
                        }
                    }
                    sb.setLength(0);
                } else {
                    sb.append((char) b);
                }
            }
        } catch (IOException e) {
            log.error("Read log history error", e);
        }

        return result;
    }

    @GetMapping("/services")
    public Set<String> getServices() {
        return serviceLogPaths.keySet();
    }

    private LogEntry parseLogLine(String service, String line) {
        LogEntry entry = new LogEntry();
        entry.setService(service);
        entry.setRaw(line);
        entry.setTimestamp(System.currentTimeMillis());

        // 简单解析日志级别
        if (line.contains("ERROR")) {
            entry.setLevel("ERROR");
        } else if (line.contains("WARN")) {
            entry.setLevel("WARN");
        } else {
            entry.setLevel("INFO");
        }

        return entry;
    }

    // 内部类：日志 tail 线程
    private static class LogTailer {
        private final String service;
        private final String path;
        private final String levelFilter;
        private final SseEmitter emitter;
        private volatile boolean running = true;
        private Thread thread;

        LogTailer(String service, String path, String levelFilter, SseEmitter emitter) {
            this.service = service;
            this.path = path;
            this.levelFilter = levelFilter;
            this.emitter = emitter;
        }

        void start() {
            thread = new Thread(() -> {
                File file = new File(path);
                long lastSize = file.exists() ? file.length() : 0;

                while (running) {
                    try {
                        if (!file.exists()) {
                            Thread.sleep(1000);
                            continue;
                        }

                        long currentSize = file.length();
                        if (currentSize > lastSize) {
                            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                                raf.seek(lastSize);
                                String line;
                                while ((line = raf.readLine()) != null) {
                                    String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                                    LogEntry entry = parseLogLine(service, decoded);
                                    if (levelFilter == null || entry.getLevel().equals(levelFilter)) {
                                        emitter.send(SseEmitter.event()
                                                .name("log")
                                                .data(entry));
                                    }
                                }
                            }
                            lastSize = currentSize;
                        } else if (currentSize < lastSize) {
                            // 文件被轮转，从头读取
                            lastSize = 0;
                        }

                        Thread.sleep(500);
                    } catch (Exception e) {
                        if (running) {
                            log.error("Tail error for {}: {}", service, e.getMessage());
                        }
                    }
                }
            }, "log-tailer-" + service);
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            if (thread != null) {
                thread.interrupt();
            }
        }

        private LogEntry parseLogLine(String service, String line) {
            LogEntry entry = new LogEntry();
            entry.setService(service);
            entry.setRaw(line);
            entry.setTimestamp(System.currentTimeMillis());

            if (line.contains("ERROR")) {
                entry.setLevel("ERROR");
            } else if (line.contains("WARN")) {
                entry.setLevel("WARN");
            } else {
                entry.setLevel("INFO");
            }

            return entry;
        }
    }
}
