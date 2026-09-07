package com.minduc.happabi.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectWithdrawalRequest {

    @NotBlank
    @Size(max = 500)
    private String adminNote;
}
