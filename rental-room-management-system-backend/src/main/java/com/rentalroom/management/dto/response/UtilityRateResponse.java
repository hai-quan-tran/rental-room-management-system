package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.UtilityRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UtilityRateResponse(
        Long id,
        Long branchId,
        Long extraFeeCategoryId,
        String extraFeeCategoryName,
        String unit,
        BigDecimal unitPrice,
        LocalDate effectiveFrom,
        LocalDateTime createdAt
) {
    public static UtilityRateResponse from(UtilityRate rate) {
        return new UtilityRateResponse(
                rate.getId(),
                rate.getBranch().getId(),
                rate.getExtraFeeCategory().getId(),
                rate.getExtraFeeCategory().getName(),
                rate.getExtraFeeCategory().getUnit(),
                rate.getUnitPrice(),
                rate.getEffectiveFrom(),
                rate.getCreatedAt()
        );
    }
}
