package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MeterReadingRequest(
        @NotNull(message = "Danh mục không được để trống") Long extraFeeCategoryId,
        @NotNull(message = "Tháng không được để trống") @Min(1) @Max(12) Integer billMonth,
        @NotNull(message = "Năm không được để trống") @Min(2000) @Max(2100) Integer billYear,
        /** Only required for a room+category's first-ever reading; otherwise auto-chained from the previous reading. */
        @DecimalMin(value = "0", message = "Chỉ số cũ không được âm") BigDecimal oldReading,
        @NotNull(message = "Chỉ số mới không được để trống") @DecimalMin(value = "0", message = "Chỉ số mới không được âm") BigDecimal newReading,
        String note
) {
}
