package com.minduc.happabi.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class AiOutputSanitizer {

    private static final String FALLBACK_ANSWER = """
            M\u00ecnh ch\u01b0a c\u00f3 \u0111\u1ee7 th\u00f4ng tin \u0111\u00e3 x\u00e1c th\u1ef1c \u0111\u1ec3 tr\u1ea3 l\u1eddi ch\u1eafc ch\u1eafn v\u1ec1 y\u00eau c\u1ea7u n\u00e0y.
            B\u1ea1n vui l\u00f2ng ki\u1ec3m tra tr\u1ef1c ti\u1ebfp trong \u1ee9ng d\u1ee5ng Happabi ho\u1eb7c li\u00ean h\u1ec7 b\u1ed9 ph\u1eadn h\u1ed7 tr\u1ee3 \u0111\u1ec3 \u0111\u01b0\u1ee3c h\u01b0\u1edbng d\u1eabn ch\u00ednh x\u00e1c.
            N\u1ebfu b\u00e9 c\u00f3 d\u1ea5u hi\u1ec7u b\u1ea5t th\u01b0\u1eddng nh\u01b0 s\u1ed1t cao, kh\u00f3 th\u1edf, b\u1ecf b\u00fa, li b\u00ec, t\u00edm t\u00e1i, co gi\u1eadt ho\u1eb7c n\u00f4n \u00f3i nhi\u1ec1u, b\u1ea1n n\u00ean \u0111\u01b0a b\u00e9 \u0111\u1ebfn c\u01a1 s\u1edf y t\u1ebf ngay.
            """;

    private static final List<String> LEAKAGE_MARKERS = List.of(
            "system prompt",
            "developer instruction",
            "hidden instruction",
            "hidden context",
            "prompt structure",
            "chain of thought",
            "reasoning",
            "the policy says",
            "policy says",
            "the context says",
            "context says",
            "the user asks",
            "user asks",
            "to respond to the user question",
            "must respond",
            "must not",
            "we must",
            "i must",
            "internal rule",
            "internal policy"
    );

    private static final List<String> FINAL_ANSWER_STARTS = List.of(
            "chao me",
            "chao ban",
            "da me",
            "da ban",
            "me co the",
            "me nen",
            "me vui long",
            "ban co the",
            "ban nen",
            "ban vui long",
            "hi me",
            "hi ban"
    );

    public SanitizedAiOutput sanitize(String output) {
        String normalized = stripProviderMetadata(normalize(output));
        if (normalized.isBlank()) {
            return new SanitizedAiOutput(FALLBACK_ANSWER.trim(), true, true);
        }

        boolean leakageDetected = containsLeakage(normalized);
        if (!leakageDetected) {
            return new SanitizedAiOutput(stripMarkdown(normalized).trim(), false, false);
        }

        String extractedAnswer = extractFinalAnswer(normalized);
        if (!extractedAnswer.isBlank() && !containsLeakage(extractedAnswer)) {
            log.warn("[AI_CHAT] Sanitized prompt leakage from AI output by extracting final answer.");
            return new SanitizedAiOutput(stripMarkdown(extractedAnswer).trim(), true, false);
        }

        log.warn("[AI_CHAT] Blocked AI output because it contained prompt leakage.");
        return new SanitizedAiOutput(FALLBACK_ANSWER.trim(), true, true);
    }

    private String normalize(String output) {
        if (output == null) {
            return "";
        }
        return output
                .replace('\u00A0', ' ')
                .replace("\u53bb", "")
                .trim();
    }

    private String stripMarkdown(String value) {
        return value
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("__(.*?)__", "$1")
                .replaceAll("\\*(.*?)\\*", "$1")
                .replaceAll("_(.*?)_", "$1")
                .replaceAll("`{1,3}([^`]+)`{1,3}", "$1")
                .replaceAll("(?m)^\\s{0,3}>\\s?", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "- ")
                .replaceAll("(?m)^\\s*---+\\s*$", "")
                .trim();
    }

    private String stripProviderMetadata(String value) {
        String[] lines = value.split("\\R", -1);
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (isProviderMetadataLine(trimmed)) {
                continue;
            }
            String withoutFinalAnswerLabel = removeFinalAnswerLabel(trimmed);
            if (!withoutFinalAnswerLabel.isBlank()) {
                if (!cleaned.isEmpty()) {
                    cleaned.append('\n');
                }
                cleaned.append(withoutFinalAnswerLabel);
            }
        }
        return cleaned.toString().trim();
    }

    private boolean isProviderMetadataLine(String line) {
        if (line.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(line, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.matches("^(user\\s+safety|model\\s+safety|content\\s+safety|safety|safety\\s+rating|moderation|moderation\\s+result)\\s*[:：].*$")
                || normalized.matches("^(safe|unsafe|blocked|not\\s+blocked)$");
    }

    private String removeFinalAnswerLabel(String line) {
        return line.replaceFirst("(?i)^\\s*(final\\s+answer|answer|response)\\s*[:：]\\s*", "").trim();
    }

    private boolean containsLeakage(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return LEAKAGE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private String extractFinalAnswer(String value) {
        int startIndex = -1;
        int lineStart = 0;
        String[] lines = value.split("\\R", -1);
        for (String line : lines) {
            String normalizedLine = stripDiacritics(line).toLowerCase(Locale.ROOT).trim();
            if (FINAL_ANSWER_STARTS.stream().anyMatch(normalizedLine::startsWith)) {
                startIndex = lineStart;
            }
            lineStart += line.length() + 1;
        }
        if (startIndex < 0) {
            return "";
        }
        return value.substring(startIndex);
    }

    private String stripDiacritics(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D');
    }

    public record SanitizedAiOutput(String content, boolean leakageDetected, boolean blocked) {
    }
}
