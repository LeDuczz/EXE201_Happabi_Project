package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.entity.KnowledgeItem;
import com.minduc.happabi.enums.KnowledgeStatus;
import com.minduc.happabi.repository.KnowledgeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgVectorKnowledgeBaseServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private KnowledgeItemRepository knowledgeItemRepository;

    private PgVectorKnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        service = new PgVectorKnowledgeBaseService(vectorStore, knowledgeItemRepository);
    }

    @Test
    void getKnowledgeItemsReturnsPagedSearchResultsWithTrimmedKeyword() {
        var pageable = PageRequest.of(1, 10);
        var item = knowledgeItem("Massage sau sinh", KnowledgeStatus.VERIFIED);
        when(knowledgeItemRepository.searchItems(KnowledgeStatus.VERIFIED, "massage", pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 11));

        var page = service.getKnowledgeItems(KnowledgeStatus.VERIFIED, "  massage  ", pageable);

        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Massage sau sinh");
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(KnowledgeStatus.VERIFIED);
        verify(knowledgeItemRepository).searchItems(KnowledgeStatus.VERIFIED, "massage", pageable);
    }

    @Test
    void getKnowledgeItemsTreatsBlankKeywordAsMissing() {
        var pageable = PageRequest.of(0, 20);
        when(knowledgeItemRepository.searchItems(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var page = service.getKnowledgeItems(null, "   ", pageable);

        assertThat(page.getContent()).isEmpty();
        verify(knowledgeItemRepository).searchItems(null, null, pageable);
    }

    private KnowledgeItem knowledgeItem(String title, KnowledgeStatus status) {
        return KnowledgeItem.builder()
                .id(UUID.randomUUID())
                .title(title)
                .question("Question")
                .answer("Answer")
                .context("Context")
                .status(status)
                .language("vi")
                .sourceType("ADMIN")
                .vectorIndexed(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
