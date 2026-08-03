package com.rentalroom.management.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CheckoutRequest(
        @NotNull(message = "Ngày trả phòng không được để trống") LocalDate checkoutDate,
        String note,
        @NotEmpty(message = "Phải có ít nhất 1 vật dụng trong checklist") @Valid List<CheckoutItemRequest> items
) {
}
