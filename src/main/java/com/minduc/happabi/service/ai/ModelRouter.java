package com.minduc.happabi.service.ai;

public interface ModelRouter {

    String route(ChatIntent intent, String userMessage);
}
