package com.minduc.happabi.service.ai;

public record RagDocument(
        String title,
        String content,
        String source,
        Double score
) {
}
