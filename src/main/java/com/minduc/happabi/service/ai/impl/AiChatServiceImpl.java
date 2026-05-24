package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.request.ai.CreateConversationRequest;
import com.minduc.happabi.dto.request.ai.SendAiMessageRequest;
import com.minduc.happabi.dto.response.ai.AiChatResponse;
import com.minduc.happabi.dto.response.ai.AiMessageResponse;
import com.minduc.happabi.dto.response.ai.ConversationResponse;
import com.minduc.happabi.entity.ChatMessage;
import com.minduc.happabi.entity.Conversation;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.ChatRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AiChatErrorCode;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.ai.IAiChatModelClient;
import com.minduc.happabi.service.ai.IAiChatService;
import com.minduc.happabi.service.ai.ChatHistoryService;
import com.minduc.happabi.service.ai.ChatIntent;
import com.minduc.happabi.service.ai.IntentDetector;
import com.minduc.happabi.service.ai.IModelRouter;
import com.minduc.happabi.service.ai.PromptBuilder;
import com.minduc.happabi.service.ai.RagDocument;
import com.minduc.happabi.service.ai.IRagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements IAiChatService {

    private static final int RAG_TOP_K = 5;

    private final UserRepository userRepository;
    private final UserIdentityProviderRepository identityProviderRepository;
    private final ChatHistoryService chatHistoryService;
    private final IntentDetector intentDetector;
    private final IModelRouter modelRouter;
    private final IRagRetrievalService ragRetrievalService;
    private final PromptBuilder promptBuilder;
    private final IAiChatModelClient aiChatModelClient;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ConversationResponse createConversation(CreateConversationRequest request) {
        User user = getCurrentUser();
        String title = request == null ? null : request.getTitle();
        return chatHistoryService.createConversation(user.getId(), title);
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public List<ConversationResponse> getCurrentUserConversations() {
        User user = getCurrentUser();
        return chatHistoryService.getConversations(user.getId());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public List<AiMessageResponse> getMessages(UUID conversationId) {
        User user = getCurrentUser();
        return chatHistoryService.getMessages(conversationId, user.getId());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public AiChatResponse sendMessage(UUID conversationId, SendAiMessageRequest request) {
        String userText = normalizeMessage(request == null ? null : request.getMessage());
        User user = getCurrentUser();
        Conversation conversation = chatHistoryService.requireConversation(conversationId, user.getId());

        ChatMessage userMessage = chatHistoryService.saveMessage(
                conversation,
                ChatRole.USER,
                userText,
                null,
                null,
                null
        );

        ChatIntent intent = intentDetector.detect(userText);
        String model = modelRouter.route(intent, userText);
        List<RagDocument> ragDocuments = ragRetrievalService.retrieve(userText, intent, RAG_TOP_K);
        List<ChatMessage> recentMessages = chatHistoryService.getRecentMessages(conversation);

        String systemPrompt = promptBuilder.buildSystemPrompt(intent, ragDocuments);
        String userPrompt = promptBuilder.buildUserPrompt(userText, recentMessages);
        String assistantText = aiChatModelClient.generate(model, systemPrompt, userPrompt);

        ChatMessage assistantMessage = chatHistoryService.saveMessage(
                conversation,
                ChatRole.ASSISTANT,
                assistantText,
                model,
                null,
                null
        );

        log.info("[AI_CHAT] conversationId={} userId={} intent={} model={}",
                conversationId, user.getId(), intent, model);

        return new AiChatResponse(
                conversationId,
                chatHistoryService.toMessageResponse(userMessage),
                chatHistoryService.toMessageResponse(assistantMessage),
                model
        );
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new AppException(AiChatErrorCode.AI_CHAT_MESSAGE_REQUIRED);
        }
        return message.trim();
    }

    private User getCurrentUser() {
        String cognitoSub = AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED));

        return userRepository.findByCognitoSub(cognitoSub)
                .or(() -> identityProviderRepository.findUserByProviderUidWithRolesAndProviders(cognitoSub))
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }
}
