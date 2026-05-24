package com.minduc.happabi.service.ai;

public interface IAiChatModelClient {

    String generate(String model, String systemPrompt, String userPrompt);
}
