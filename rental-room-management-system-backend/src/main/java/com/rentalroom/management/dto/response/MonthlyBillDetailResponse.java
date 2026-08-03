package com.rentalroom.management.dto.response;

import java.util.List;

public record MonthlyBillDetailResponse(
        MonthlyBillResponse bill,
        List<ExtraFeeItemResponse> extraFeeItems,
        List<PaymentResponse> payments
) {
}
