package com.minduc.happabi.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class OpenAiConfig {

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${openai.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${openai.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Bean
    @Qualifier("openAiRestClient")
    public RestClient openAiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(openAiBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

}
