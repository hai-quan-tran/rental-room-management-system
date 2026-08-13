package com.rentalroom.management.dto.request;

import com.rentalroom.management.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Login credentials to provision when creating an employee — fullName is not repeated here, it comes from EmployeeCreateRequest.fullName. */
public record EmployeeAccountRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống") String username,
        @NotBlank(message = "Mật khẩu không được để trống") @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự") String password,
        @NotNull(message = "Vai trò không được để trống") Role role
) {
}
