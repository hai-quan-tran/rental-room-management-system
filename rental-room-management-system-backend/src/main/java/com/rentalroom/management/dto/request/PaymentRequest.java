package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRequest(
        @NotNull(message = "Số tiền không được để trống") @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0") BigDecimal amount,
        @NotNull(message = "Ngày thanh toán không được để trống") LocalDate paymentDate,
        /** Free text per spec — no fixed method list. */
        String method,
        String note
) {
}
