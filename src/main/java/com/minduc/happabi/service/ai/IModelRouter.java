package com.minduc.happabi.service.ai;

public interface IModelRouter {

    String route(ChatIntent intent, String userMessage);

}
