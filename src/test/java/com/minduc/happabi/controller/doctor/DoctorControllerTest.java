package com.minduc.happabi.controller.doctor;

import com.minduc.happabi.integration.s3.S3ObjectDownload;
import com.minduc.happabi.service.doctor.IDoctorNurseReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoctorControllerTest {

    private final IDoctorNurseReviewService reviewService = mock(IDoctorNurseReviewService.class);
    private final DoctorController controller = new DoctorController(reviewService);

    @Test
    void kycFileResponseFallsBackWhenDownloadedBytesAndMetadataAreMissing() {
        UUID profileId = UUID.randomUUID();
        when(reviewService.getKycDocumentFile(profileId, "front"))
                .thenReturn(new S3ObjectDownload(null, null, null));

        var response = controller.getKycDocumentFile(profileId, "front");

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentLength()).isZero();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void certificationFileResponseUsesProvidedMetadata() {
        UUID certificationId = UUID.randomUUID();
        when(reviewService.getCertificationDocumentFile(certificationId))
                .thenReturn(new S3ObjectDownload(new byte[]{1, 2}, "image/jpeg", 10L));

        var response = controller.getCertificationDocumentFile(certificationId);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(10L);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("nurse-certification");
        assertThat(response.getBody()).containsExactly(1, 2);
    }
}
