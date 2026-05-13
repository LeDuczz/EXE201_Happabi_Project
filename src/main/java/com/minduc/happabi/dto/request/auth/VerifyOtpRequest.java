package com.minduc.happabi.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Số điện thoại không được để trống.")
    @Pattern(
            regexp = "^\\+84[3-9]\\d{8}$",
            message = "Số điện thoại phải theo định dạng E.164 của Việt Nam (ví dụ: +84901234567)."
    )
    private String phone;

    @NotBlank(message = "Mã OTP không được để trống.")
    @Pattern(regexp = "^\\d{6}$", message = "Mã OTP phải gồm đúng 6 chữ số.")
    private String otpCode;
}
