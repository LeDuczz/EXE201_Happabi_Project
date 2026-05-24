package com.minduc.happabi.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenRouterConfig {

    @Value("${openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    @Value("${openrouter.site-url:https://happabi.vn}")
    private String siteUrl;

    @Value("${openrouter.app-name:Happabi}")
    private String appName;

    @Bean
    @Qualifier("openRouterRestClient")
    public RestClient openRouterRestClient() {
        return RestClient.builder()
                .baseUrl(openRouterBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", siteUrl)
                .defaultHeader("X-Title", appName)
                .build();
    }
}
