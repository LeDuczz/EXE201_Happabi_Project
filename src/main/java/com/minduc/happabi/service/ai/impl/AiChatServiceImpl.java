package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.document.RagDocument;
import com.minduc.happabi.dto.request.ai.CreateConversationRequest;
import com.minduc.happabi.dto.request.ai.SendAiMessageRequest;
import com.minduc.happabi.dto.response.ai.AiChatResponse;
import com.minduc.happabi.dto.response.ai.AiMessageResponse;
import com.minduc.happabi.dto.response.ai.ConversationResponse;
import com.minduc.happabi.entity.ChatMessage;
import com.minduc.happabi.entity.Conversation;
import com.minduc.happabi.entity.KnowledgeItem;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.ChatRole;
import com.minduc.happabi.enums.KnowledgeStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AiChatErrorCode;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.repository.KnowledgeItemRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.ai.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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
    private final AiOutputSanitizer aiOutputSanitizer;
    private final IKnowledgeBaseService knowledgeBaseService;
    private final KnowledgeItemRepository knowledgeItemRepository;

    @Value("${ai-chat.rag.high-threshold:0.85}")
    private Double highThreshold;

    @Value("${ai-chat.provider:openrouter}")
    private String aiChatProvider;

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
        KnowledgeItem exactVerifiedItem = findExactVerifiedKnowledge(userText);
        String model = modelRouter.route(intent, userText);
        RagDocument bestMatch = null;

        String assistantText;
        String resolutionSource;
        boolean pendingReviewCreated = false;

        if (exactVerifiedItem != null) {
            assistantText = exactVerifiedItem.getAnswer();
            resolutionSource = "VERIFIED_KNOWLEDGE_EXACT";
            model = "knowledge-db";
        } else {
            List<RagDocument> ragDocuments = ragRetrievalService.retrieve(userText, intent, RAG_TOP_K);
            List<ChatMessage> recentMessages = chatHistoryService.getRecentMessages(conversation);
            bestMatch = bestMatch(ragDocuments);
            boolean highConfidenceMatch = bestMatch != null
                    && bestMatch.getAnswer() != null
                    && scoreOrZero(bestMatch) >= highThreshold;

            if (highConfidenceMatch) {
                assistantText = bestMatch.getAnswer();
                resolutionSource = "VERIFIED_KNOWLEDGE";
                model = "knowledge-db";
            } else {
                List<RagDocument> promptContext = bestMatch == null ? List.of() : ragDocuments;
                String systemPrompt = promptBuilder.buildSystemPrompt(intent, promptContext);
                String userPrompt = promptBuilder.buildUserPrompt(userText, recentMessages);
                String rawAssistantText = aiChatModelClient.generate(model, systemPrompt, userPrompt);
                AiOutputSanitizer.SanitizedAiOutput sanitizedOutput = aiOutputSanitizer.sanitize(rawAssistantText);
                assistantText = sanitizedOutput.content();
                resolutionSource = promptContext.isEmpty()
                        ? providerResolutionSource("NO_MATCH")
                        : providerResolutionSource("WITH_RAG");
                if (sanitizedOutput.leakageDetected()) {
                    resolutionSource = resolutionSource + "_SANITIZED";
                }
                if (promptContext.isEmpty() && !sanitizedOutput.blocked()) {
                    knowledgeBaseService.createPendingReview(user.getId(), conversationId, userText, assistantText, null);
                    pendingReviewCreated = true;
                }
            }
        }

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

        return AiChatResponse.builder()
                .conversationId(conversationId)
                .userMessage(chatHistoryService.toMessageResponse(userMessage))
                .assistantMessage(chatHistoryService.toMessageResponse(assistantMessage))
                .modelUsed(model)
                .resolutionSource(resolutionSource)
                .ragScore(bestMatch == null ? null : bestMatch.getScore())
                .pendingReviewCreated(pendingReviewCreated)
                .build();
    }

    private RagDocument bestMatch(List<RagDocument> ragDocuments) {
        return ragDocuments.stream()
                .max(Comparator.comparingDouble(this::scoreOrZero))
                .orElse(null);
    }

    private double scoreOrZero(RagDocument document) {
        return document.getScore() == null ? 0 : document.getScore();
    }

    private KnowledgeItem findExactVerifiedKnowledge(String userText) {
        return knowledgeItemRepository
                .findFirstByQuestionIgnoreCaseAndStatusOrderByUpdatedAtDesc(userText, KnowledgeStatus.VERIFIED)
                .orElse(null);
    }

    private String providerResolutionSource(String suffix) {
        String provider = aiChatProvider == null || aiChatProvider.isBlank() ? "AI" : aiChatProvider;
        return provider.toUpperCase() + "_" + suffix;
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
