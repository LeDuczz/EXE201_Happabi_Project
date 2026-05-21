package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.dto.request.ai.UpsertKnowledgeChunkRequest;
import com.minduc.happabi.dto.response.ai.KnowledgeChunkResponse;
import com.minduc.happabi.service.ai.EmbeddingClient;
import com.minduc.happabi.service.ai.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PgVectorKnowledgeBaseService implements KnowledgeBaseService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;

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
        String embedding = PgVectorRagRetrievalService.toVectorLiteral(embeddingClient.embed(title + "\n\n" + content));

        jdbcTemplate.update("""
                        INSERT INTO ai_knowledge_chunks (
                            knowledge_chunk_id, title, content, source_type, source_id,
                            language, verified, embedding, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector, now(), now())
                        """,
                id,
                title,
                content,
                sourceType,
                sourceId,
                language,
                verified,
                embedding);

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
