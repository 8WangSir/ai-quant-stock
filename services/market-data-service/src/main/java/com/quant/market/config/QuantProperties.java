package com.quant.market.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "quant")
public class QuantProperties {
    private DataSourceConfig dataSource = new DataSourceConfig();
    private TickflowConfig tickflow = new TickflowConfig();
    private JuheConfig juhe = new JuheConfig();
    private MomaapiConfig momaapi = new MomaapiConfig();
    private InfowayConfig infoway = new InfowayConfig();
    private SyncConfig sync = new SyncConfig();

    @Data
    public static class DataSourceConfig {
        private String primary = "momaapi";
        private String fallback = "tickflow";
    }

    @Data
    public static class TickflowConfig {
        private String baseUrl = "https://api.tickflow.org";
        private String apiKey;
        private String universeId = "CN_Equity_A";
    }

    @Data
    public static class JuheConfig {
        private String apiKey;
    }

    @Data
    public static class MomaapiConfig {
        private String token;
    }

    @Data
    public static class InfowayConfig {
        private String apiKey;
    }

    @Data
    public static class SyncConfig {
        private int historyYears = 5;
    }
}
