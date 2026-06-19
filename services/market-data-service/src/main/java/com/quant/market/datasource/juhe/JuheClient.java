package com.quant.market.datasource.juhe;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 聚合数据（juhe.cn）股票 API 客户端
 * Docker 容器内可用，提供实时行情数据
 */
@Slf4j
@Component
public class JuheClient {

    private static final String BASE_URL = "https://web.juhe.cn/finance/stock/hs";

    private final RestClient restClient;
    private final String apiKey;

    public JuheClient(com.quant.market.config.QuantProperties properties) {
        this.apiKey = properties.getJuhe().getApiKey();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(15000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 获取个股实时行情
     * gid 格式: sh600000, sz000001
     */
    public JsonNode fetchQuote(String gid) {
        String url = BASE_URL + "?key=" + apiKey + "&gid=" + gid + "&type=all";
        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("聚合数据请求失败 gid={}: {}", gid, e.getMessage());
            return null;
        }
    }
}
