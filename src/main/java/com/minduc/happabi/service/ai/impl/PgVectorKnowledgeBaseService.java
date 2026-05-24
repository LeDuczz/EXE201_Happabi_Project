package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.dto.request.ai.UpsertKnowledgeChunkRequest;
import com.minduc.happabi.dto.response.ai.KnowledgeChunkResponse;
import com.minduc.happabi.dto.response.ai.KnowledgeItemResponse;
import com.minduc.happabi.entity.KnowledgeItem;
import com.minduc.happabi.enums.KnowledgeStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.CommonErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.KnowledgeItemRepository;
import com.minduc.happabi.service.ai.IKnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PgVectorKnowledgeBaseService implements IKnowledgeBaseService {

    private final VectorStore vectorStore;
    private final KnowledgeItemRepository knowledgeItemRepository;

    @Override
    @Transactional
    @LogExecution
    @AuditAction(action = "UPSERT_KNOWLEDGE_CHUNK", resourceType = "KNOWLEDGE_ITEM")
    @TimedAction("UPSERT_KNOWLEDGE_CHUNK")
    public KnowledgeChunkResponse upsertKnowledgeChunk(UpsertKnowledgeChunkRequest request) {
        String title = request.getTitle().trim();
        String question = request.getQuestion().trim();
        String answer = request.getAnswer().trim();
        String content = request.getContent().trim();
        String sourceType = blankToNull(request.getSourceType());
        String sourceId = blankToNull(request.getSourceId());
        String language = request.getLanguage() == null || request.getLanguage().isBlank()
                ? "vi"
                : request.getLanguage().trim();
        boolean verified = request.getVerified() == null || request.getVerified();

        KnowledgeItem item = knowledgeItemRepository.save(KnowledgeItem.builder()
                .title(title)
                .question(question)
                .answer(answer)
                .context(content)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .language(language)
                .status(verified ? KnowledgeStatus.VERIFIED : KnowledgeStatus.PENDING_REVIEW)
                .vectorIndexed(false)
                .build());

        if (verified) {
            indexVerifiedItem(item);
        }

        OffsetDateTime now = OffsetDateTime.now();
        return KnowledgeChunkResponse.builder()
                .id(item.getId())
                .title(title)
                .question(question)
                .answer(answer)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .language(language)
                .verified(verified)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Override
    @Transactional
    @LogExecution
    @TimedAction("CREATE_PENDING_REVIEW_KNOWLEDGE_ITEM")
    @AuditAction(action = "CREATE_PENDING_REVIEW_KNOWLEDGE_ITEM", resourceType = "KNOWLEDGE_ITEM")
    public KnowledgeItemResponse createPendingReview(UUID userId,
                                                     UUID conversationId,
                                                     String question,
                                                     String answer,
                                                     String context) {
        KnowledgeItem item = knowledgeItemRepository.save(KnowledgeItem.builder()
                .title(buildTitle(question))
                .question(question)
                .answer(answer)
                .context(context)
                .sourceType("AI_GENERATED")
                .sourceId(conversationId == null ? null : conversationId.toString())
                .language("vi")
                .status(KnowledgeStatus.PENDING_REVIEW)
                .conversationId(conversationId)
                .userId(userId)
                .vectorIndexed(false)
                .build());
        return toResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    @LogExecution
    @AuditAction(action = "GET_PENDING_REVIEW_KNOWLEDGE_ITEMS", resourceType = "KNOWLEDGE_ITEM")
    @TimedAction("GET_PENDING_REVIEW_KNOWLEDGE_ITEMS")
    public List<KnowledgeItemResponse> getPendingReviewItems() {
        return knowledgeItemRepository.findByStatusOrderByCreatedAtDesc(KnowledgeStatus.PENDING_REVIEW)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @LogExecution
    @AuditAction(action = "REVIEW_KNOWLEDGE_ITEM", resourceType = "KNOWLEDGE_ITEM")
    @TimedAction("REVIEW_KNOWLEDGE_ITEM")
    public KnowledgeItemResponse reviewKnowledgeItem(UUID knowledgeItemId, UUID reviewerId, boolean approved) {
        KnowledgeItem item = knowledgeItemRepository.findById(knowledgeItemId)
                .orElseThrow(() -> new AppException(CommonErrorCode.NOT_FOUND, "Knowledge item not found."));
        item.setStatus(approved ? KnowledgeStatus.VERIFIED : KnowledgeStatus.REJECTED);
        item.setReviewedBy(reviewerId);
        item.setReviewedAt(OffsetDateTime.now());

        if (approved && !Boolean.TRUE.equals(item.getVectorIndexed())) {
            indexVerifiedItem(item);
        }

        return toResponse(knowledgeItemRepository.save(item));
    }

    @Override
    @Transactional
    @LogExecution
    @AuditAction(action = "REINDEX_KNOWLEDGE_ITEM", resourceType = "KNOWLEDGE_ITEM")
    @TimedAction("REINDEX_KNOWLEDGE_ITEM")
    public KnowledgeItemResponse reindexKnowledgeItem(UUID knowledgeItemId) {
        KnowledgeItem item = knowledgeItemRepository.findById(knowledgeItemId)
                .orElseThrow(() -> new AppException(CommonErrorCode.NOT_FOUND, "Knowledge item not found."));
        if (item.getStatus() != KnowledgeStatus.VERIFIED) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "Only VERIFIED knowledge can be indexed.");
        }
        indexVerifiedItem(item);
        return toResponse(knowledgeItemRepository.save(item));
    }

    @Override
    @Transactional
    @LogExecution
    @AuditAction(action = "REINDEX_VERIFIED_KNOWLEDGE_ITEMS", resourceType = "KNOWLEDGE_ITEM")
    @TimedAction("REINDEX_VERIFIED_KNOWLEDGE_ITEMS")
    public List<KnowledgeItemResponse> reindexVerifiedKnowledgeItems() {
        return knowledgeItemRepository
                .findByStatusAndVectorIndexedFalseOrderByUpdatedAtDesc(KnowledgeStatus.VERIFIED)
                .stream()
                .map(item -> {
                    indexVerifiedItem(item);
                    return toResponse(knowledgeItemRepository.save(item));
                })
                .toList();
    }

    private void indexVerifiedItem(KnowledgeItem item) {
        vectorStore.add(List.of(new Document(item.getQuestion(), Map.of(
                "knowledgeItemId", item.getId().toString(),
                "title", item.getTitle(),
                "question", item.getQuestion(),
                "answer", item.getAnswer(),
                "context", item.getContext() == null ? "" : item.getContext(),
                "sourceType", item.getSourceType() == null ? "" : item.getSourceType(),
                "sourceId", item.getSourceId() == null ? "" : item.getSourceId(),
                "language", item.getLanguage() == null ? "vi" : item.getLanguage(),
                "verified", true
        ))));
        item.setVectorIndexed(true);
    }

    private KnowledgeItemResponse toResponse(KnowledgeItem item) {
        return KnowledgeItemResponse.builder()
                .id(item.getId())
                .question(item.getQuestion())
                .answer(item.getAnswer())
                .context(item.getContext())
                .title(item.getTitle())
                .sourceType(item.getSourceType())
                .sourceId(item.getSourceId())
                .language(item.getLanguage())
                .status(item.getStatus())
                .vectorIndexed(item.getVectorIndexed())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private String buildTitle(String question) {
        String normalized = question == null ? "AI generated answer" : question.trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
