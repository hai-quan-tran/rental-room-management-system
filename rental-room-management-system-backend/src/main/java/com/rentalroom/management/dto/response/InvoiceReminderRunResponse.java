package com.rentalroom.management.dto.response;

public record InvoiceReminderRunResponse(
        int targetYear,
        int targetMonth,
        int roomsFlagged,
        int branchesNotified,
        int branchesSkipped
) {
}
