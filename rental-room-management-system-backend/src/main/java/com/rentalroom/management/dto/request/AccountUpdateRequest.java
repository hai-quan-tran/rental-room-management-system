package com.rentalroom.management.dto.request;

import com.rentalroom.management.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountUpdateRequest(
        @NotBlank(message = "Họ tên không được để trống") String fullName,
        @NotBlank(message = "Tên đăng nhập không được để trống") String username,
        @NotNull(message = "Vai trò không được để trống") Role role
) {
}
