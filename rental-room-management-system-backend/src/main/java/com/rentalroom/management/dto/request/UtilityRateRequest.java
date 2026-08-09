package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UtilityRateRequest(
        @NotNull(message = "Danh mục không được để trống") Long extraFeeCategoryId,
        @NotNull(message = "Đơn giá không được để trống") @DecimalMin(value = "0", message = "Đơn giá không được âm") BigDecimal unitPrice,
        @NotNull(message = "Ngày hiệu lực không được để trống") LocalDate effectiveFrom
) {
}
