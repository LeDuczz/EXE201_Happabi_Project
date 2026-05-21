package com.minduc.happabi.service.ai.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorKnowledgeBaseInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${ai-chat.rag.pgvector.enabled:true}")
    private boolean pgVectorEnabled;

    @Value("${ai-chat.rag.pgvector.initialize-schema:true}")
    private boolean initializeSchema;

    @Value("${ai-chat.rag.embedding-dimension:1536}")
    private int embeddingDimension;

    @Override
    public void run(ApplicationArguments args) {
        if (!pgVectorEnabled || !initializeSchema) {
            return;
        }

        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_knowledge_chunks (
                        knowledge_chunk_id UUID PRIMARY KEY,
                        title VARCHAR(240) NOT NULL,
                        content TEXT NOT NULL,
                        source_type VARCHAR(120),
                        source_id VARCHAR(160),
                        language VARCHAR(80),
                        verified BOOLEAN NOT NULL DEFAULT TRUE,
                        embedding vector(%d) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """.formatted(embeddingDimension));
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunks_embedding
                    ON ai_knowledge_chunks USING ivfflat (embedding vector_cosine_ops)
                    WITH (lists = 100)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunks_verified_language
                    ON ai_knowledge_chunks (verified, language)
                    """);
        } catch (RuntimeException e) {
            log.warn("[AI_RAG] pgvector schema initialization failed: {}", e.getMessage());
        }
    }
}
