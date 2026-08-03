package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public record HandoverItemRequest(
        @NotBlank(message = "Tên vật dụng không được để trống") String itemName,
        @NotNull @Min(1) Integer quantity,
        String note
) {
}
