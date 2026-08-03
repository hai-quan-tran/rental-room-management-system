package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BranchRequest(
        @NotBlank(message = "Tên chi nhánh không được để trống") String name,
        @NotBlank(message = "Địa chỉ không được để trống") String address,
        Long managerAccountId
) {
}
