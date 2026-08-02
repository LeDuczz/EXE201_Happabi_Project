package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.entity.KnowledgeItem;
import com.minduc.happabi.enums.KnowledgeStatus;
import com.minduc.happabi.repository.KnowledgeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
        var specCaptor = org.mockito.ArgumentCaptor.forClass(Specification.class);
        when(knowledgeItemRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 11));

        var page = service.getKnowledgeItems(KnowledgeStatus.VERIFIED, "  massage  ", pageable);

        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Massage sau sinh");
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(KnowledgeStatus.VERIFIED);
        verify(knowledgeItemRepository).findAll(specCaptor.capture(), eq(pageable));
        executeSpec(specCaptor.getValue());
    }

    @Test
    void getKnowledgeItemsTreatsBlankKeywordAsMissing() {
        var pageable = PageRequest.of(0, 20);
        var specCaptor = org.mockito.ArgumentCaptor.forClass(Specification.class);
        when(knowledgeItemRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var page = service.getKnowledgeItems(null, "   ", pageable);

        assertThat(page.getContent()).isEmpty();
        verify(knowledgeItemRepository).findAll(specCaptor.capture(), eq(pageable));
        executeSpec(specCaptor.getValue());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeSpec(Specification spec) {
        Root<KnowledgeItem> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Expression<String> lowerExpression = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get(anyString())).thenReturn(path);
        when(criteriaBuilder.equal(any(), any())).thenReturn(predicate);
        when(criteriaBuilder.lower(any())).thenReturn(lowerExpression);
        when(criteriaBuilder.like(any(), anyString())).thenReturn(predicate);
        when(criteriaBuilder.or(any(Predicate.class), any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, criteriaBuilder);
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
