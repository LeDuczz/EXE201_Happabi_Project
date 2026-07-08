package com.minduc.happabi.dto.event;

public record S3UploadedObjectRollbackCleanupEvent(
        String key,
        String reason
) {
}
