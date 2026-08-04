package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HandoverItemRequest(
        @NotNull(message = "Vật dụng không được để trống") Long itemId,
        @NotNull @Min(1) Integer quantity,
        String note
) {
}
