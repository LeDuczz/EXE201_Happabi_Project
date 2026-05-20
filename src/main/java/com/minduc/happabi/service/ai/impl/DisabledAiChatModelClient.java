package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AiChatErrorCode;
import com.minduc.happabi.service.ai.AiChatModelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AiChatModelClient.class)
public class DisabledAiChatModelClient implements AiChatModelClient {

    @Override
    public String generate(String model, String systemPrompt, String userPrompt) {
        throw new AppException(AiChatErrorCode.AI_CHAT_CONFIGURATION_MISSING,
                "AI chat provider is disabled. Set AI_CHAT_MODEL_PROVIDER=openai and OPENAI_API_KEY to enable it.");
    }
}
