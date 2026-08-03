package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExtraFeeCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống") String name,
        String unit
) {
}
