package com.minduc.happabi.service.ai;

import com.minduc.happabi.dto.request.ai.CreateConversationRequest;
import com.minduc.happabi.dto.request.ai.SendAiMessageRequest;
import com.minduc.happabi.dto.response.ai.AiChatResponse;
import com.minduc.happabi.dto.response.ai.AiMessageResponse;
import com.minduc.happabi.dto.response.ai.ConversationResponse;

import java.util.List;
import java.util.UUID;

public interface AiChatService {

    ConversationResponse createConversation(CreateConversationRequest request);

    List<ConversationResponse> getCurrentUserConversations();

    List<AiMessageResponse> getMessages(UUID conversationId);

    AiChatResponse sendMessage(UUID conversationId, SendAiMessageRequest request);
}
