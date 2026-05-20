package com.minduc.happabi.dto.request.nurse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateNurseKycRequest {
    @NotBlank
    @Size(max = 20)
    private String cccdNumber;
    @NotBlank
    @Size(max = 100)
    private String cccdName;
    private LocalDate cccdDob;
    @Size(max = 2000)
    private String cccdAddress;
}
