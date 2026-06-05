package com.minduc.happabi.integration.s3;

public record S3ObjectDownload(
        byte[] bytes,
        String contentType,
        Long contentLength
) {
}
