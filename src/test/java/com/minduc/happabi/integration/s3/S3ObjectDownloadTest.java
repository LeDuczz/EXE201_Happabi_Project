package com.minduc.happabi.integration.s3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class S3ObjectDownloadTest {

    @Test
    void usesArrayContentForEqualityHashCodeAndString() {
        S3ObjectDownload first = new S3ObjectDownload(new byte[]{1, 2, 3}, "image/png", 3L);
        S3ObjectDownload second = new S3ObjectDownload(new byte[]{1, 2, 3}, "image/png", 3L);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.toString()).contains("bytes=[1, 2, 3]");
    }

    @Test
    void protectsStoredBytesWithDefensiveCopies() {
        byte[] source = new byte[]{1, 2, 3};
        S3ObjectDownload download = new S3ObjectDownload(source, "application/pdf", 3L);

        source[0] = 9;
        byte[] returned = download.bytes();
        returned[1] = 8;

        assertThat(download.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void equalsHandlesSameReferenceAndDifferentTypes() {
        S3ObjectDownload download = new S3ObjectDownload(new byte[]{1}, "text/plain", 1L);

        assertThat(download.equals(download)).isTrue();
        assertThat(download).isNotEqualTo("not a download");
    }
}
