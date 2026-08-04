package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemRequest(
        @NotBlank(message = "Tên vật dụng không được để trống") String name,
        @NotNull(message = "Giá không được để trống") @DecimalMin(value = "0", message = "Giá không được âm") BigDecimal price,
        @NotNull(message = "Số lượng hiện có không được để trống") @Min(value = 0, message = "Số lượng hiện có không được âm") Integer quantityAvailable,
        @NotNull(message = "Số lượng mặc định mỗi phòng không được để trống") @Min(value = 1, message = "Số lượng mặc định mỗi phòng phải >= 1") Integer defaultQuantityPerRoom
) {
}
