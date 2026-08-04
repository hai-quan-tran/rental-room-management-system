package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CheckoutItemRequest(
        @NotNull(message = "Vật dụng không được để trống") Long roomTypeHandoverItemId,
        @NotNull(message = "Số lượng hư hỏng không được để trống") @Min(value = 0, message = "Số lượng hư hỏng không được âm") Integer damagedQuantity,
        @NotNull(message = "Số lượng mất không được để trống") @Min(value = 0, message = "Số lượng mất không được âm") Integer lostQuantity,
        @NotNull @DecimalMin(value = "0", message = "Tiền trừ không được âm") BigDecimal deductionAmount,
        String note
) {
}
