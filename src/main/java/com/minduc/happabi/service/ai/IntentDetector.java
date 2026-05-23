package com.minduc.happabi.service.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class IntentDetector {

    private static final Set<String> MEDICAL_KEYWORDS = Set.of(
            "sot", "đau", "dau", "thuoc", "thuốc", "bú", "bu", "sữa", "sua",
            "bé", "be", "mang thai", "sau sinh", "bác sĩ", "bac si"
    );

    private static final Set<String> SUPPORT_KEYWORDS = Set.of(
            "booking", "dat lich", "đặt lịch", "nurse", "y tá", "yta",
            "hồ sơ", "ho so", "thanh toán", "payment", "hủy", "huy"
    );

    public ChatIntent detect(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, MEDICAL_KEYWORDS)) {
            return ChatIntent.MEDICAL_GUIDANCE;
        }
        if (containsAny(normalized, SUPPORT_KEYWORDS)) {
            return ChatIntent.PLATFORM_SUPPORT;
        }
        return ChatIntent.GENERAL;
    }

    private boolean containsAny(String value, Set<String> keywords) {
        return keywords.stream().anyMatch(value::contains);
    }
}
