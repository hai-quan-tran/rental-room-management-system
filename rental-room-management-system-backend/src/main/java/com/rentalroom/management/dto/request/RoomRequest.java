package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RoomRequest(
        @NotBlank(message = "Mã phòng không được để trống") String roomCode,
        @NotNull(message = "Loại phòng không được để trống") Long roomTypeId,
        @NotNull(message = "Giá thuê không được để trống") @DecimalMin(value = "0", message = "Giá thuê không được âm") BigDecimal monthlyRent
) {
}
