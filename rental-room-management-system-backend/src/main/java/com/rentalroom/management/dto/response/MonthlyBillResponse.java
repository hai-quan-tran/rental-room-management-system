package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.MonthlyBill;
import com.rentalroom.management.enums.PaymentStatus;

import java.math.BigDecimal;

public record MonthlyBillResponse(
        Long id,
        Long contractId,
        Integer billMonth,
        Integer billYear,
        BigDecimal rentAmount,
        BigDecimal totalExtraFee,
        BigDecimal wifiFee,
        BigDecimal parkingFee,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        PaymentStatus paymentStatus
) {
    public static MonthlyBillResponse from(MonthlyBill bill) {
        return new MonthlyBillResponse(
                bill.getId(),
                bill.getContract().getId(),
                bill.getBillMonth(),
                bill.getBillYear(),
                bill.getRentAmount(),
                bill.getTotalExtraFee(),
                bill.getWifiFee(),
                bill.getParkingFee(),
                bill.getTotalAmount(),
                bill.getPaidAmount(),
                bill.getRemainingAmount(),
                bill.getPaymentStatus()
        );
    }
}
