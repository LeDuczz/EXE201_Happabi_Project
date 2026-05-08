package com.minduc.happabi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Số điện thoại không được để trống.")
    @Pattern(
            regexp = "^\\+84[3-9]\\d{8}$",
            message = "Số điện thoại phải theo định dạng E.164 của Việt Nam (ví dụ: +84901234567)."
    )
    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống.")
    private String password;
}
