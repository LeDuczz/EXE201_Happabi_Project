package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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
