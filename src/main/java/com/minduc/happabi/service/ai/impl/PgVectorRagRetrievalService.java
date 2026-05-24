package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.dto.document.RagDocument;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.service.ai.ChatIntent;
import com.minduc.happabi.service.ai.IRagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgVectorRagRetrievalService implements IRagRetrievalService {

    private final VectorStore vectorStore;

    @Value("${ai-chat.rag.medium-threshold:0.60}")
    private Double mediumThreshold;

    @Override
    @TimedAction("RAG_RETRIEVAL")
    @LogExecution
    public List<RagDocument> retrieve(String query, ChatIntent intent, int topK) {
        try {
            return vectorStore.similaritySearch(SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(mediumThreshold)
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
                .content(resolveContent(document, metadata))
                .source(buildSource(metadata))
                .score(document.getScore())
                .question(stringMetadata(metadata, "question"))
                .answer(stringMetadata(metadata, "answer"))
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

    private String resolveContent(Document document, Map<String, Object> metadata) {
        String context = stringMetadata(metadata, "context");
        if (context != null && !context.isBlank()) {
            return context;
        }
        String answer = stringMetadata(metadata, "answer");
        if (answer != null && !answer.isBlank()) {
            return answer;
        }
        return document.getText();
    }
}
