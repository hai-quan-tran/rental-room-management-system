package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        BigDecimal amount,
        LocalDate paymentDate,
        String method,
        String note,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getMethod(),
                payment.getNote(),
                payment.getCreatedAt()
        );
    }
}
