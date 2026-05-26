package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AiChatErrorCode;
import com.minduc.happabi.service.ai.IAiChatModelClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat", name = "provider", havingValue = "gemini")
public class GeminiAiChatModelClient implements IAiChatModelClient {

    private final ChatClient.Builder chatClientBuilder;

    @Value("${spring.ai.google.genai.api-key:}")
    private String apiKey;

    @Override
    public String generate(String model, String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException(AiChatErrorCode.AI_CHAT_CONFIGURATION_MISSING,
                    "GEMINI_API_KEY or GOOGLE_API_KEY is not configured.");
        }

        try {
            String content = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE,
                        "Gemini returned an empty response.");
            }
            return content.trim();
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[AI_CHAT] Gemini request failed: {}", e.getMessage());
            throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE, e);
        }
    }
}
