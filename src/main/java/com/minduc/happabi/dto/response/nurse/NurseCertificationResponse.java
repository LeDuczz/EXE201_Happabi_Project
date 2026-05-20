package com.minduc.happabi.dto.response.nurse;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class NurseCertificationResponse {
    private UUID id;
    private String certName;
    private String issuedBy;
    private LocalDate issuedDate;
    private LocalDate expiryDate;
    private Boolean hasDocument;
    private Boolean verified;
    private OffsetDateTime verifiedAt;
}
