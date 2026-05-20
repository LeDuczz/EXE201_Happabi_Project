package com.minduc.happabi.service.ai;

import com.minduc.happabi.entity.ChatMessage;
import com.minduc.happabi.enums.ChatRole;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    public String buildSystemPrompt(ChatIntent intent, List<RagDocument> contextDocuments) {
        String context = contextDocuments.isEmpty()
                ? "No verified RAG context was found for this question."
                : contextDocuments.stream()
                        .map(doc -> "- " + safe(doc.title()) + ": " + safe(doc.content())
                                + (doc.source() == null ? "" : " Source: " + doc.source()))
                        .collect(Collectors.joining("\n"));

        return """
                You are Happabi's AI assistant for mothers, nurses, and care coordination.
                Answer in the user's language, with a calm and practical tone.
                Use only verified context when giving Happabi platform facts.
                For medical or baby-care questions, provide general guidance only and advise contacting a qualified doctor for urgent, risky, or diagnosis-specific cases.
                Do not invent policies, prices, nurse availability, diagnoses, or treatment plans.

                Intent: %s

                Verified context:
                %s
                """.formatted(intent, context);
    }

    public String buildUserPrompt(String userQuestion, List<ChatMessage> recentMessages) {
        String history = recentMessages.isEmpty()
                ? "No previous messages."
                : recentMessages.stream()
                        .filter(message -> message.getRole() != ChatRole.SYSTEM)
                        .map(message -> message.getRole() + ": " + message.getContent())
                        .collect(Collectors.joining("\n"));

        return """
                Recent conversation:
                %s

                User question:
                %s
                """.formatted(history, userQuestion);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
