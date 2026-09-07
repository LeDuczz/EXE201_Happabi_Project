package com.minduc.happabi.dto.request.nurse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
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
