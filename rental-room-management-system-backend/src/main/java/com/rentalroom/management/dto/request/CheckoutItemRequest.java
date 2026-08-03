package com.rentalroom.management.dto.request;

import com.rentalroom.management.enums.ChecklistItemStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CheckoutItemRequest(
        @NotNull(message = "Vật dụng không được để trống") Long roomTypeHandoverItemId,
        @NotNull(message = "Tình trạng không được để trống") ChecklistItemStatus status,
        @NotNull @DecimalMin(value = "0", message = "Tiền trừ không được âm") BigDecimal deductionAmount,
        String note
) {
}
