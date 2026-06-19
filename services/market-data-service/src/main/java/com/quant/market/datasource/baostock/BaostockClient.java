package com.quant.market.datasource.baostock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BaoStock Python 客户端桥接
 */
@Slf4j
@Component
public class BaostockClient {

    private final String pythonExecutable;
    private final String scriptPath;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BaostockClient() {
        this.pythonExecutable = "python";
        String localPath = "c:/Users/wangw/ai-quant-stock/python/data_sync/baostock_client.py";
        String dockerPath = "/app/python/data_sync/baostock_client.py";
        if (new java.io.File(localPath).exists()) {
            this.scriptPath = localPath;
        } else {
            this.scriptPath = dockerPath;
        }
    }

    public List<Map<String, Object>> fetchStockList() {
        try {
            Map<String, Object> result = runPython("stock_list");
            Boolean success = (Boolean) result.get("success");
            if (!Boolean.TRUE.equals(success)) {
                log.error("BaoStock 获取股票列表失败: {}", result.get("error"));
                return null;
            }
            return (List<Map<String, Object>>) result.get("data");
        } catch (Exception e) {
            log.error("BaoStock 获取股票列表异常", e);
            return null;
        }
    }

    public List<Map<String, Object>> fetchDailyBars(List<String> codes, String startDate, String endDate) {
        try {
            File tempFile = File.createTempFile("baostock_", ".json");
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(tempFile), java.nio.charset.StandardCharsets.UTF_8)) {
                objectMapper.writeValue(writer, codes);
            }
            Map<String, Object> result = runPython("daily_full", startDate, endDate, "@" + tempFile.getAbsolutePath());
            tempFile.delete();
            
            Boolean success = (Boolean) result.get("success");
            if (!Boolean.TRUE.equals(success)) {
                log.error("BaoStock 获取日线数据失败: {}", result.get("error"));
                return null;
            }
            return (List<Map<String, Object>>) result.get("data");
        } catch (Exception e) {
            log.error("BaoStock 获取日线数据异常", e);
            return null;
        }
    }

    public List<Map<String, Object>> fetchDailyBarsIncr(List<String> codes, String date) {
        try {
            File tempFile = File.createTempFile("baostock_", ".json");
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(tempFile), java.nio.charset.StandardCharsets.UTF_8)) {
                objectMapper.writeValue(writer, codes);
            }
            Map<String, Object> result = runPython("daily_incr", date, "@" + tempFile.getAbsolutePath());
            tempFile.delete();
            
            Boolean success = (Boolean) result.get("success");
            if (!Boolean.TRUE.equals(success)) {
                log.error("BaoStock 获取增量日线数据失败: {}", result.get("error"));
                return null;
            }
            return (List<Map<String, Object>>) result.get("data");
        } catch (Exception e) {
            log.error("BaoStock 获取增量日线数据异常", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runPython(String... args) {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(scriptPath);
        for (String arg : args) {
            command.add(arg);
        }

        try {
            log.info("BaoStock 执行命令: {}", command);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("BaoStock Python 脚本退出码 {}: {}", exitCode, output);
                return Map.of("success", false, "error", "exit code " + exitCode);
            }

            String json = output.toString().trim();
            String[] lines = json.split("\n");
            String lastLine = lines[lines.length - 1];
            
            return objectMapper.readValue(lastLine, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("BaoStock 执行失败: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
