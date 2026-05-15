package com.minduc.happabi.dto.event;

public record S3ObjectDeleteRequestedEvent(
        String key,
        String reason
) {
}
