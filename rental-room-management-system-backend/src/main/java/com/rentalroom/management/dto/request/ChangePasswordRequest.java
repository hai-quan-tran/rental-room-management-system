package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự") String newPassword
) {
}
