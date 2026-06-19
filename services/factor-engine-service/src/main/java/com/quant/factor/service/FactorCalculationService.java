package com.quant.factor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FactorCalculationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${quant.python.home:../../python}")
    private String pythonHome;

    @Value("${quant.python.executable:python}")
    private String pythonExecutable;

    public void calculateIndicators(LocalDate tradeDate) {
        runPython("indicators", tradeDate);
    }

    public void calculateIndustryStrength(LocalDate tradeDate) {
        runPython("industry", tradeDate);
    }

    public void calculateScores(LocalDate tradeDate) {
        runPython("scores", tradeDate);
    }

    public void generateRecommendPool(LocalDate tradeDate) {
        runPython("recommend", tradeDate);
    }

    private String getPythonHome() {
        String base = System.getProperty("user.dir");
        // 如果在 services/xxx 子目录中，自动向上找到项目根目录
        if (base.contains("/services/") || base.contains("\\services\\")) {
            int idx = base.indexOf("/services/");
            if (idx == -1) idx = base.indexOf("\\services\\");
            if (idx > 0) {
                base = base.substring(0, idx);
            }
        }
        return base + "/python";
    }

    private void runPython(String command, LocalDate tradeDate) {
        try {
            String resolvedHome = getPythonHome();
            Path script = Path.of(resolvedHome, "factor_engine/batch_calculator.py").toAbsolutePath().normalize();
            log.info("Python script path: {}", script);
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
                log.error("Factor calculation failed: {}\n{}", command, output);
                return;
            }
            JsonNode result = objectMapper.readTree(output);
            log.info("Factor calculation completed: {}", result);
        } catch (Exception e) {
            log.error("Factor calculation error for {}", command, e);
        }
    }
}
