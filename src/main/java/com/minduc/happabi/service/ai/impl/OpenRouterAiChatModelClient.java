package com.minduc.happabi.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AiChatErrorCode;
import com.minduc.happabi.service.ai.IAiChatModelClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai-chat", name = "provider", havingValue = "openrouter", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenRouterAiChatModelClient implements IAiChatModelClient {

    @Qualifier("openRouterRestClient")
    private final RestClient openRouterRestClient;

    @Value("${openrouter.api-key:}")
    private String apiKey;

    @Value("${openrouter.temperature:0.2}")
    private Double temperature;

    @Override
    public String generate(String model, String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException(AiChatErrorCode.AI_CHAT_CONFIGURATION_MISSING,
                    "OPENROUTER_API_KEY is not configured.");
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "temperature", temperature,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            JsonNode response = openRouterRestClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseBody) -> {
                        throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE,
                                "OpenRouter status=" + responseBody.getStatusCode());
                    })
                    .body(JsonNode.class);

            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE,
                        "OpenRouter returned an empty response.");
            }
            return content.trim();
        } catch (AppException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("[AI_CHAT] OpenRouter request failed: {}", e.getMessage());
            throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE, e);
        }
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode content = choices.get(0).path("message").path("content");
        return content.isTextual() ? content.asText() : null;
    }
}
