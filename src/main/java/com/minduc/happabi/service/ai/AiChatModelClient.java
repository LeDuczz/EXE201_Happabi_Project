package com.minduc.happabi.service.ai;

public interface AiChatModelClient {

    String generate(String model, String systemPrompt, String userPrompt);
}
