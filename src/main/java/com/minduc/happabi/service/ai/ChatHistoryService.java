package com.minduc.happabi.service.ai;

import com.minduc.happabi.dto.response.ai.AiMessageResponse;
import com.minduc.happabi.dto.response.ai.ConversationResponse;
import com.minduc.happabi.entity.ChatMessage;
import com.minduc.happabi.entity.Conversation;
import com.minduc.happabi.enums.ChatRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AiChatErrorCode;
import com.minduc.happabi.repository.ChatMessageRepository;
import com.minduc.happabi.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private static final String DEFAULT_TITLE = "New conversation";

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ConversationResponse createConversation(UUID userId, String requestedTitle) {
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .title(normalizeTitle(requestedTitle))
                .build();
        return toConversationResponse(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(UUID userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Conversation requireConversation(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AppException(AiChatErrorCode.AI_CHAT_CONVERSATION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<AiMessageResponse> getMessages(UUID conversationId, UUID userId) {
        Conversation conversation = requireConversation(conversationId, userId);
        return chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getRecentMessages(Conversation conversation) {
        return chatMessageRepository.findTop12ByConversationOrderByCreatedAtDesc(conversation)
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .toList();
    }

    @Transactional
    public ChatMessage saveMessage(Conversation conversation,
                                   ChatRole role,
                                   String content,
                                   String modelUsed,
                                   Integer inputTokens,
                                   Integer outputTokens) {
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .modelUsed(modelUsed)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();
        ChatMessage saved = chatMessageRepository.save(message);
        conversation.setUpdatedAt(OffsetDateTime.now());
        conversationRepository.save(conversation);
        return saved;
    }

    public ConversationResponse toConversationResponse(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getUserId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    public AiMessageResponse toMessageResponse(ChatMessage message) {
        return new AiMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getModelUsed(),
                message.getInputTokens(),
                message.getOutputTokens(),
                message.getCreatedAt()
        );
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        String trimmed = title.trim();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160);
    }
}
