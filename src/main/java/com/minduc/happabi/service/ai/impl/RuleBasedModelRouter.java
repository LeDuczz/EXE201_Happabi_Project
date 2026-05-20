package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.service.ai.ChatIntent;
import com.minduc.happabi.service.ai.ModelRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedModelRouter implements ModelRouter {

    @Value("${ai-chat.models.default:gpt-4o-mini}")
    private String defaultModel;

    @Value("${ai-chat.models.reasoning:gpt-4o-mini}")
    private String reasoningModel;

    @Value("${ai-chat.models.fast:gpt-4o-mini}")
    private String fastModel;

    @Override
    public String route(ChatIntent intent, String userMessage) {
        if (intent == ChatIntent.MEDICAL_GUIDANCE) {
            return reasoningModel;
        }
        if (intent == ChatIntent.PLATFORM_SUPPORT || userMessage.length() < 500) {
            return fastModel;
        }
        return defaultModel;
    }
}
