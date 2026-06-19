package com.quant.market.datasource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PythonBridge {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${quant.python.home:../../python}")
    private String pythonHome;

    @Value("${quant.python.executable:python3}")
    private String pythonExecutable;

    /** Python 脚本超时时间（秒） */
    private static final long TIMEOUT_SECONDS = 60;

    public List<Map<String, Object>> runScript(String script, String... args) {
        Path scriptPath = Path.of(pythonHome, script).toAbsolutePath().normalize();
        String[] command = new String[2 + args.length];
        command[0] = pythonExecutable;
        command[1] = scriptPath.toString();
        System.arraycopy(args, 0, command, 2, args.length);

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            // 不合并 stderr 到 stdout，分开处理
            Process process = builder.start();

            // 设置超时，防止 Python 进程挂起
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Python 脚本超时（{}s），已强制终止: {}", TIMEOUT_SECONDS, String.join(" ", command));
                return Collections.emptyList();
            }

            // 只读取 stdout 用于 JSON 解析
            String stdout;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                stdout = reader.lines().collect(Collectors.joining("\n"));
            }

            // 读取 stderr 用于日志记录
            String stderr;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                stderr = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python script failed (exit={}): {}\nstderr: {}", exitCode, String.join(" ", command),
                        stderr.length() > 300 ? stderr.substring(0, 300) + "..." : stderr);
                return Collections.emptyList();
            }

            // 如果有 stderr warning，仅记录不阻断
            if (!stderr.isBlank() && !stderr.contains("UserWarning") && !stderr.contains("DeprecationWarning")) {
                log.warn("Python stderr: {}", stderr.length() > 300 ? stderr.substring(0, 300) + "..." : stderr);
            }

            if (stdout == null || stdout.isBlank()) {
                log.warn("Python script returned empty output: {}", String.join(" ", command));
                return Collections.emptyList();
            }

            return objectMapper.readValue(stdout, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Python script execution error: {} - {}", String.join(" ", command), e.getMessage());
            return Collections.emptyList();
        }
    }
}
