package com.rentalroom.management.dto.request;

import com.rentalroom.management.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** fullName is not editable here anymore — it is owned by the linked Employee record, if any (see EmployeeService). */
public record AccountUpdateRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống") String username,
        @NotNull(message = "Vai trò không được để trống") Role role
) {
}
