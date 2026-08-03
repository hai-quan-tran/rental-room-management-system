package com.rentalroom.management.dto.response;

import java.util.List;

public record BulkMonthlyBillCreateResponse(
        List<MonthlyBillListResponse> createdBills,
        int alreadyExistsCount,
        int notApplicableCount
) {
}
