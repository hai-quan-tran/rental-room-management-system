package com.rentalroom.management.dto.response;

import java.math.BigDecimal;

public record RevenuePointResponse(
        int year,
        int month,
        BigDecimal totalAmount
) {
}
