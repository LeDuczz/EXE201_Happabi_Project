package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.dto.request.ai.UpsertKnowledgeChunkRequest;
import com.minduc.happabi.dto.response.ai.KnowledgeChunkResponse;
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

    @Override
    @Transactional
    public KnowledgeChunkResponse upsertKnowledgeChunk(UpsertKnowledgeChunkRequest request) {
        UUID id = UUID.randomUUID();
        String title = request.getTitle().trim();
        String content = request.getContent().trim();
        String sourceType = blankToNull(request.getSourceType());
        String sourceId = blankToNull(request.getSourceId());
        String language = request.getLanguage() == null || request.getLanguage().isBlank()
                ? "vi"
                : request.getLanguage().trim();
        boolean verified = request.getVerified() == null || request.getVerified();

        vectorStore.add(List.of(new Document(content, Map.of(
                "knowledgeChunkId", id.toString(),
                "title", title,
                "sourceType", sourceType == null ? "" : sourceType,
                "sourceId", sourceId == null ? "" : sourceId,
                "language", language,
                "verified", verified
        ))));

        OffsetDateTime now = OffsetDateTime.now();
        return KnowledgeChunkResponse.builder()
                .id(id)
                .title(title)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .language(language)
                .verified(verified)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
