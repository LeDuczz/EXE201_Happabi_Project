package com.minduc.happabi.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AiChatErrorCode;
import com.minduc.happabi.service.ai.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingClient implements EmbeddingClient {

    @Qualifier("openAiRestClient")
    private final RestClient openAiRestClient;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${ai-chat.rag.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    @Override
    public List<Double> embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException(AiChatErrorCode.AI_CHAT_CONFIGURATION_MISSING,
                    "OPENAI_API_KEY is required for embeddings.");
        }

        try {
            JsonNode response = openAiRestClient.post()
                    .uri("/embeddings")
                    .body(Map.of("model", embeddingModel, "input", text))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseBody) -> {
                        throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE,
                                "OpenAI embeddings status=" + responseBody.getStatusCode());
                    })
                    .body(JsonNode.class);

            JsonNode embedding = response == null ? null : response.path("data").path(0).path("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE,
                        "OpenAI embeddings response is invalid.");
            }

            List<Double> values = new ArrayList<>(embedding.size());
            embedding.forEach(item -> values.add(item.asDouble()));
            return values;
        } catch (AppException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("[AI_CHAT] OpenAI embedding request failed: {}", e.getMessage());
            throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE, e);
        }
    }
}
