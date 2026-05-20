package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AiChatErrorCode;
import com.minduc.happabi.service.ai.AiChatModelClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpringAiChatModelClient implements AiChatModelClient {

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public SpringAiChatModelClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generate(String model, String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException(AiChatErrorCode.AI_CHAT_CONFIGURATION_MISSING,
                    "SPRING_AI_OPENAI_API_KEY or OPENAI_API_KEY is not configured.");
        }

        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(0.2)
                            .build())
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE,
                        "AI provider returned an empty response.");
            }
            return content.trim();
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[AI_CHAT] Provider call failed: {}", e.getMessage());
            throw new AppException(AiChatErrorCode.AI_CHAT_PROVIDER_UNAVAILABLE, e);
        }
    }
}
