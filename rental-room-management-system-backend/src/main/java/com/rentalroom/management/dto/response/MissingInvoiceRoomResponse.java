package com.rentalroom.management.dto.response;

public record MissingInvoiceRoomResponse(
        long roomId,
        String roomCode,
        long branchId,
        String branchName,
        int missingYear,
        int missingMonth
) {
}
