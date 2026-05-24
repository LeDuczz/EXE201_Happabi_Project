package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.service.ai.ChatIntent;
import com.minduc.happabi.service.ai.RagDocument;
import com.minduc.happabi.service.ai.IRagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgVectorRagRetrievalService implements IRagRetrievalService {

    private final VectorStore vectorStore;

    @Override
    public List<RagDocument> retrieve(String query, ChatIntent intent, int topK) {
        try {
            return vectorStore.similaritySearch(SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .build())
                    .stream()
                    .filter(this::isVerified)
                    .map(this::toRagDocument)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("[AI_RAG] Vector retrieval failed, continuing without RAG context: {}", e.getMessage());
            return List.of();
        }
    }

    private RagDocument toRagDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return RagDocument.builder()
                .title(stringMetadata(metadata, "title"))
                .content(document.getText())
                .source(buildSource(metadata))
                .score(null)
                .build();
    }

    private boolean isVerified(Document document) {
        Object verified = document.getMetadata().get("verified");
        return verified == null || Boolean.parseBoolean(String.valueOf(verified));
    }

    private String buildSource(Map<String, Object> metadata) {
        String sourceType = stringMetadata(metadata, "sourceType");
        String sourceId = stringMetadata(metadata, "sourceId");
        if (sourceType == null || sourceType.isBlank()) {
            return sourceId;
        }
        if (sourceId == null || sourceId.isBlank()) {
            return sourceType;
        }
        return sourceType + ":" + sourceId;
    }

    private String stringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
