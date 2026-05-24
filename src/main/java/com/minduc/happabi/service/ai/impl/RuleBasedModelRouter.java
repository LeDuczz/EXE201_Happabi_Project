package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.service.ai.ChatIntent;
import com.minduc.happabi.service.ai.IModelRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedModelRouter implements IModelRouter {

    @Value("${ai-chat.models.default:gpt-4o-mini}")
    private String defaultModel;

    @Value("${ai-chat.models.reasoning:gpt-4o-mini}")
    private String reasoningModel;

    @Value("${ai-chat.models.fast:gpt-4o-mini}")
    private String fastModel;

    @Value("${openrouter.enabled:true}")
    private boolean openRouterEnabled;

    @Value("${openrouter.model:openrouter/auto}")
    private String openRouterModel;

    @Override
    public String route(ChatIntent intent, String userMessage) {
        if (openRouterEnabled) {
            return openRouterModel;
        }
        if (intent == ChatIntent.MEDICAL_GUIDANCE) {
            return reasoningModel;
        }
        if (intent == ChatIntent.PLATFORM_SUPPORT || userMessage.length() < 500) {
            return fastModel;
        }
        return defaultModel;
    }
}
