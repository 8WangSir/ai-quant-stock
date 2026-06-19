package com.quant.strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PythonRunner {

    @Value("${quant.python.home:../../python}")
    private String pythonHome;

    @Value("${quant.python.executable:python}")
    private String pythonExecutable;

    public void run(String command, LocalDate tradeDate) {
        try {
            Path script = Path.of(pythonHome, "factor_engine/batch_calculator.py").toAbsolutePath().normalize();
            ProcessBuilder builder = new ProcessBuilder(
                    pythonExecutable, script.toString(), command, tradeDate.toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python runner failed: {}\n{}", command, output);
            }
        } catch (Exception e) {
            log.error("Python runner error", e);
        }
    }
}
