package com.bean.beanbi.config;

import io.github.briqt.spark4j.SparkClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 星火大模型客户端配置
 *
 * @author sami
 */
@ConfigurationProperties(prefix = "spark")
@Configuration
@Data
public class SparkClientConfig {

    private String appid;
    private String apiSecret;
    private String apiKey;

    @Bean
    public SparkClient getSparkClient() {
        SparkClient sparkClient = new SparkClient();

        sparkClient.appid = appid;
        sparkClient.apiSecret = apiSecret;
        sparkClient.apiKey = apiKey;
        return sparkClient;
    }
}
