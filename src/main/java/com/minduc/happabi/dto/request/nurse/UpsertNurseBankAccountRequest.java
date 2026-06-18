package com.minduc.happabi.dto.request.nurse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertNurseBankAccountRequest {

    @NotBlank
    @Size(max = 120)
    private String bankName;

    @NotBlank
    @Size(max = 60)
    private String bankAccountNumber;

    @NotBlank
    @Size(max = 120)
    private String bankAccountHolder;
}
