package com.minduc.happabi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Yêu cầu đặt lại mật khẩu sau khi người dùng đã nhận được OTP qua SMS.
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Số điện thoại không được để trống.")
    @Pattern(
            regexp = "^\\+84[3-9]\\d{8}$",
            message = "Số điện thoại phải theo định dạng E.164 của Việt Nam (ví dụ: +84901234567)."
    )
    private String phone;

    @NotBlank(message = "Mã OTP không được để trống.")
    private String otpCode;

    @NotBlank(message = "Mật khẩu mới không được để trống.")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự.")
    private String newPassword;
}
