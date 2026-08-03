package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CollectDebtRequest(
        @NotNull(message = "Số tiền thu không được để trống") @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0") BigDecimal collectedAmount
) {
}
