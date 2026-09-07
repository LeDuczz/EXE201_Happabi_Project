package com.minduc.happabi.dto.request.nurse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateNurseCertificationRequest {
    @NotBlank
    @Size(max = 200)
    private String certName;
    @NotBlank
    @Size(max = 100)
    private String issuedBy;
    private LocalDate issuedDate;
    private LocalDate expiryDate;
}
