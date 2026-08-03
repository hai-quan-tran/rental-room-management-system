package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.ExtraFeeItem;

import java.math.BigDecimal;

public record ExtraFeeItemResponse(
        Long id,
        Long extraFeeCategoryId,
        String extraFeeCategoryName,
        BigDecimal amount,
        String note
) {
    public static ExtraFeeItemResponse from(ExtraFeeItem item) {
        return new ExtraFeeItemResponse(
                item.getId(),
                item.getExtraFeeCategory().getId(),
                item.getExtraFeeCategory().getName(),
                item.getAmount(),
                item.getNote()
        );
    }
}
