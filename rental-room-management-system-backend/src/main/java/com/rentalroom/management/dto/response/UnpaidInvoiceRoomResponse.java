package com.rentalroom.management.dto.response;

import java.math.BigDecimal;

public record UnpaidInvoiceRoomResponse(
        long roomId,
        String roomCode,
        long branchId,
        String branchName,
        int billYear,
        int billMonth,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount
) {
}
