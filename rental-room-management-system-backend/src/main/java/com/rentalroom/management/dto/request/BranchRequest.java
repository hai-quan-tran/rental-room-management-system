package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BranchRequest(
        @NotBlank(message = "Tên chi nhánh không được để trống") String name,
        @NotBlank(message = "Địa chỉ không được để trống") String address,
        Long managerAccountId,
        @Pattern(regexp = "\\d{6,20}", message = "Mã ngân hàng (BIN) không hợp lệ") String bankBin,
        @Pattern(regexp = "\\d{4,50}", message = "Số tài khoản ngân hàng không hợp lệ") String bankAccountNumber,
        String bankAccountName
) {
}
