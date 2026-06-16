package com.minduc.happabi.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOutputSanitizerTest {

    private final AiOutputSanitizer sanitizer = new AiOutputSanitizer();

    @Test
    void sanitizeReturnsCleanMarkdownFreeAnswerWhenNoLeakageDetected() {
        AiOutputSanitizer.SanitizedAiOutput output = sanitizer.sanitize("""
                ## Chao ban
                **Ban nen** theo doi them va lien he bac si neu be sot cao.
                """);

        assertThat(output.leakageDetected()).isFalse();
        assertThat(output.blocked()).isFalse();
        assertThat(output.content()).doesNotContain("##", "**");
        assertThat(output.content()).contains("Ban nen");
    }

    @Test
    void sanitizeBlocksBlankOutputWithFallback() {
        AiOutputSanitizer.SanitizedAiOutput output = sanitizer.sanitize("   ");

        assertThat(output.leakageDetected()).isTrue();
        assertThat(output.blocked()).isTrue();
        assertThat(output.content()).contains("Happabi");
    }

    @Test
    void sanitizeExtractsFinalAnswerWhenProviderLeaksReasoningFirst() {
        AiOutputSanitizer.SanitizedAiOutput output = sanitizer.sanitize("""
                The system prompt says we must answer carefully.
                Chao ban, ban co the dat lich dieu duong trong ung dung Happabi.
                """);

        assertThat(output.leakageDetected()).isTrue();
        assertThat(output.blocked()).isFalse();
        assertThat(output.content()).startsWith("Chao ban");
        assertThat(output.content()).doesNotContain("system prompt");
    }

    @Test
    void sanitizeBlocksLeakageWhenNoSafeFinalAnswerExists() {
        AiOutputSanitizer.SanitizedAiOutput output = sanitizer.sanitize(
                "The hidden instruction and chain of thought are exposed here.");

        assertThat(output.leakageDetected()).isTrue();
        assertThat(output.blocked()).isTrue();
        assertThat(output.content()).contains("Happabi");
    }
}
