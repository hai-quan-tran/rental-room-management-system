package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.ExtraFeeCategory;

public record ExtraFeeCategoryResponse(
        Long id,
        String name,
        String unit
) {
    public static ExtraFeeCategoryResponse from(ExtraFeeCategory category) {
        return new ExtraFeeCategoryResponse(category.getId(), category.getName(), category.getUnit());
    }
}
