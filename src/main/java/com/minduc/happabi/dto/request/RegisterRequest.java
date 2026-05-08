package com.minduc.happabi.dto.request;

import com.minduc.happabi.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Số điện thoại không được để trống.")
    @Pattern(
            regexp = "^\\+84[3-9]\\d{8}$",
            message = "Số điện thoại phải theo định dạng E.164 của Việt Nam (ví dụ: +84901234567)."
    )
    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống.")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự.")
    private String password;

    @NotBlank(message = "Họ tên không được để trống.")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự.")
    private String fullName;

    /**
     * Role to assign. Only {@code MOTHER} and {@code NURSE} are allowed for LOCAL registration.
     */
    @NotNull(message = "Role không được để trống.")
    private UserRole role;
}
