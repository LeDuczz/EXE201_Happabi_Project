package com.minduc.happabi.service.ai.impl;

import com.minduc.happabi.service.ai.ChatIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedModelRouterTest {

    private final RuleBasedModelRouter router = new RuleBasedModelRouter();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(router, "defaultModel", "default-model");
        ReflectionTestUtils.setField(router, "reasoningModel", "reasoning-model");
        ReflectionTestUtils.setField(router, "fastModel", "fast-model");
        ReflectionTestUtils.setField(router, "openRouterModel", "openrouter-model");
    }

    @Test
    void routeUsesOpenRouterModelWhenProviderIsOpenRouter() {
        ReflectionTestUtils.setField(router, "aiChatProvider", "openrouter");

        assertThat(router.route(ChatIntent.MEDICAL_GUIDANCE, "message")).isEqualTo("openrouter-model");
    }

    @Test
    void routeUsesReasoningModelForMedicalGuidanceWhenProviderIsNotOpenRouter() {
        ReflectionTestUtils.setField(router, "aiChatProvider", "google-genai");

        assertThat(router.route(ChatIntent.MEDICAL_GUIDANCE, "message")).isEqualTo("reasoning-model");
    }

    @Test
    void routeUsesFastModelForPlatformSupportAndShortGeneralMessages() {
        ReflectionTestUtils.setField(router, "aiChatProvider", "google-genai");

        assertThat(router.route(ChatIntent.PLATFORM_SUPPORT, "booking")).isEqualTo("fast-model");
        assertThat(router.route(ChatIntent.GENERAL, "short")).isEqualTo("fast-model");
    }

    @Test
    void routeUsesDefaultModelForLongGeneralMessages() {
        ReflectionTestUtils.setField(router, "aiChatProvider", "google-genai");

        assertThat(router.route(ChatIntent.GENERAL, "x".repeat(501))).isEqualTo("default-model");
    }
}
