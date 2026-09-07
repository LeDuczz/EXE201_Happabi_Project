package com.minduc.happabi.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveWithdrawalRequest {

    @Size(max = 120)
    private String bankTransactionCode;

    @Size(max = 500)
    private String adminNote;
}
