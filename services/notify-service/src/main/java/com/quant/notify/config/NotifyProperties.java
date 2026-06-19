package com.quant.notify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "quant.notify")
public class NotifyProperties {
    private TelegramConfig telegram = new TelegramConfig();
    private DingtalkConfig dingtalk = new DingtalkConfig();
    private WecomConfig wecom = new WecomConfig();

    @Data
    public static class TelegramConfig {
        private boolean enabled;
        private String botToken;
        private String chatId;
    }

    @Data
    public static class DingtalkConfig {
        private boolean enabled;
        private String webhook;
    }

    @Data
    public static class WecomConfig {
        private boolean enabled;
        private String webhook;
    }
}
