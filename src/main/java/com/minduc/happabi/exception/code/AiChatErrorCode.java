package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AiChatErrorCode implements ServiceErrorCode {

    AI_CHAT_MESSAGE_REQUIRED(HttpStatus.BAD_REQUEST, "AI chat message is required."),
    AI_CHAT_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI chat conversation was not found."),
    AI_CHAT_CONFIGURATION_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "AI chat configuration is missing."),
    AI_CHAT_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI chat provider is unavailable. Please try again.");

    HttpStatus httpStatus;
    String message;
}
