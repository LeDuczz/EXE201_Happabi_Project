package com.minduc.happabi.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentDetectorTest {

    private final IntentDetector detector = new IntentDetector();

    @Test
    void detectReturnsMedicalGuidanceForBabyHealthMessage() {
        assertThat(detector.detect("Be bi sot va bo bu")).isEqualTo(ChatIntent.MEDICAL_GUIDANCE);
    }

    @Test
    void detectReturnsPlatformSupportForBookingMessage() {
        assertThat(detector.detect("Toi muon dat lich nurse")).isEqualTo(ChatIntent.PLATFORM_SUPPORT);
    }

    @Test
    void detectPrefersMedicalGuidanceWhenMessageContainsBothMedicalAndSupportSignals() {
        assertThat(detector.detect("Dat lich vi be bi sot")).isEqualTo(ChatIntent.MEDICAL_GUIDANCE);
    }

    @Test
    void detectReturnsGeneralWhenNoKnownKeywordMatches() {
        assertThat(detector.detect("Xin chao Happabi")).isEqualTo(ChatIntent.GENERAL);
    }
}
