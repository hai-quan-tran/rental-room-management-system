package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RoomTypeRequest(
        @NotBlank(message = "Tên loại phòng không được để trống") String name,
        String area,
        String description
) {
}
