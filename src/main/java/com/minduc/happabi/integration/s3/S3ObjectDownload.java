package com.minduc.happabi.integration.s3;

import java.util.Arrays;
import java.util.Objects;

public record S3ObjectDownload(
        byte[] bytes,
        String contentType,
        Long contentLength
) {
    public S3ObjectDownload {
        bytes = bytes == null ? null : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : bytes.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof S3ObjectDownload other)) {
            return false;
        }
        return Arrays.equals(bytes, other.bytes)
                && Objects.equals(contentType, other.contentType)
                && Objects.equals(contentLength, other.contentLength);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(bytes);
        result = 31 * result + Objects.hashCode(contentType);
        result = 31 * result + Objects.hashCode(contentLength);
        return result;
    }

    @Override
    public String toString() {
        return "S3ObjectDownload[bytes=" + Arrays.toString(bytes)
                + ", contentType=" + contentType
                + ", contentLength=" + contentLength + "]";
    }
}
