package com.minduc.happabi.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveMotherRefundRequest {

    @Size(max = 120, message = "Bank transaction code must be at most 120 characters.")
    private String bankTransactionCode;

    @Size(max = 500, message = "Admin note must be at most 500 characters.")
    private String adminNote;
}
