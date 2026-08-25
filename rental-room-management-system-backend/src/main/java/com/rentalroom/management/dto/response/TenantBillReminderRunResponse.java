package com.rentalroom.management.dto.response;

public record TenantBillReminderRunResponse(
        int targetYear,
        int targetMonth,
        int billsFound,
        int emailsSent,
        int skipped
) {
}
