package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.service.ai.ChatIntent;
import com.minduc.happabi.service.ai.EmbeddingClient;
import com.minduc.happabi.service.ai.RagDocument;
import com.minduc.happabi.service.ai.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgVectorRagRetrievalService implements RagRetrievalService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;

    @Override
    public List<RagDocument> retrieve(String query, ChatIntent intent, int topK) {
        try {
            String vectorLiteral = toVectorLiteral(embeddingClient.embed(query));
            return jdbcTemplate.query("""
                            SELECT title,
                                   content,
                                   COALESCE(source_type, '') || COALESCE(':' || source_id, '') AS source,
                                   1 - (embedding <=> ?::vector) AS score
                            FROM ai_knowledge_chunks
                            WHERE verified = TRUE
                            ORDER BY embedding <=> ?::vector
                            LIMIT ?
                            """,
                    (rs, rowNum) -> RagDocument.builder()
                            .title(rs.getString("title"))
                            .content(rs.getString("content"))
                            .source(rs.getString("source"))
                            .score(rs.getDouble("score"))
                            .build(),
                    vectorLiteral,
                    vectorLiteral,
                    topK);
        } catch (RuntimeException e) {
            log.warn("[AI_RAG] Vector retrieval failed, continuing without RAG context: {}", e.getMessage());
            return List.of();
        }
    }

    static String toVectorLiteral(List<Double> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values.get(i));
        }
        return builder.append(']').toString();
    }
}
