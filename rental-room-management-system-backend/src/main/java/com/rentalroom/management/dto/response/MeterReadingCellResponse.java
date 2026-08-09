package com.rentalroom.management.dto.response;

import java.math.BigDecimal;

/** 1 metered category's reading state for 1 room+month in the "Nhập chỉ số điện nước" grid. */
public record MeterReadingCellResponse(
        Long extraFeeCategoryId,
        String categoryName,
        String unit,
        BigDecimal previousReading,
        BigDecimal currentReading,
        BigDecimal consumption,
        BigDecimal unitPrice,
        BigDecimal amount,
        String note
) {
}
