package com.rentalroom.management.dto.response;

import java.util.List;

public record BulkBillConfirmResponse(
        List<MonthlyBillListResponse> confirmedBills,
        int skippedCount
) {
}
