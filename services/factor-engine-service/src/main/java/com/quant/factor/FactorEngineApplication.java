package com.quant.factor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.quant")
public class FactorEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(FactorEngineApplication.class, args);
    }
}
