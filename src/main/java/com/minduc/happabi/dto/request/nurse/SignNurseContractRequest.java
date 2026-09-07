package com.minduc.happabi.dto.request.nurse;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignNurseContractRequest {
    @AssertTrue(message = "Contract agreement is required")
    private boolean agreed;

    @NotBlank
    @Size(max = 150)
    private String signedName;
}
