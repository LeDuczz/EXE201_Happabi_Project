package com.minduc.happabi.dto.message;

import java.time.OffsetDateTime;

public record S3ObjectDeleteMessage(
        String type,
        String bucket,
        String key,
        String reason,
        OffsetDateTime requestedAt
) {
    public static final String TYPE = "DELETE_S3_OBJECT";
}
