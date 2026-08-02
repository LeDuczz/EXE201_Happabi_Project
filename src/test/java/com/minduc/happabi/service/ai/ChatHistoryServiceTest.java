package com.minduc.happabi.service.ai;

import com.minduc.happabi.entity.Conversation;
import com.minduc.happabi.repository.ChatMessageRepository;
import com.minduc.happabi.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatHistoryService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new ChatHistoryService(conversationRepository, chatMessageRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void getConversationsReturnsPagedUserConversationsWhenKeywordIsBlank() {
        var pageable = PageRequest.of(0, 10);
        var conversation = conversation("Cham soc me bau");
        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(conversation), pageable, 1));

        var page = service.getConversations(userId, "  ", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Cham soc me bau");
        verify(conversationRepository).findByUserIdOrderByUpdatedAtDesc(userId, pageable);
    }

    @Test
    void getConversationsSearchesTitleWithTrimmedKeyword() {
        var pageable = PageRequest.of(2, 5);
        var conversation = conversation("Kich sua sau sinh");
        when(conversationRepository.findByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                userId,
                "sua",
                pageable))
                .thenReturn(new PageImpl<>(List.of(conversation), pageable, 21));

        var page = service.getConversations(userId, "  sua  ", pageable);

        assertThat(page.getTotalElements()).isEqualTo(21);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Kich sua sau sinh");
        verify(conversationRepository).findByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                userId,
                "sua",
                pageable);
    }

    private Conversation conversation(String title) {
        return Conversation.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title(title)
                .createdAt(OffsetDateTime.now().minusDays(1))
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
