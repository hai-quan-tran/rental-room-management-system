package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ExtraFeeItemRequest(
        @NotNull(message = "Danh mục chi phí không được để trống") Long extraFeeCategoryId,
        @NotNull(message = "Số tiền không được để trống") @DecimalMin(value = "0", message = "Số tiền không được âm") BigDecimal amount,
        String note
) {
}
