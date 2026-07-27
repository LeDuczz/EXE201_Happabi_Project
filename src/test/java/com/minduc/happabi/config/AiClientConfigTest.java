package com.minduc.happabi.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class AiClientConfigTest {

    @Test
    void openAiRestClientBuildsWithConfiguredValues() {
        OpenAiConfig config = new OpenAiConfig();
        ReflectionTestUtils.setField(config, "openAiApiKey", "test-openai-key");
        ReflectionTestUtils.setField(config, "openAiBaseUrl", "https://openai.example.test/v1");
        ReflectionTestUtils.setField(config, "connectTimeoutMs", 1000);
        ReflectionTestUtils.setField(config, "readTimeoutMs", 2000);

        RestClient restClient = config.openAiRestClient();

        assertThat(restClient).isNotNull();
    }

    @Test
    void openRouterRestClientBuildsWithConfiguredValues() {
        OpenRouterConfig config = new OpenRouterConfig();
        ReflectionTestUtils.setField(config, "openRouterApiKey", "test-openrouter-key");
        ReflectionTestUtils.setField(config, "openRouterBaseUrl", "https://openrouter.example.test/api/v1");
        ReflectionTestUtils.setField(config, "siteUrl", "https://happabi.example.test");
        ReflectionTestUtils.setField(config, "appName", "Happabi Test");

        RestClient restClient = config.openRouterRestClient();

        assertThat(restClient).isNotNull();
    }
}
