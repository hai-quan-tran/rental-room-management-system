package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.MonthlyBill;
import com.rentalroom.management.enums.PaymentStatus;

import java.math.BigDecimal;

/** List-row shape for the cross-room "Monthly Bills" screen — carries room/branch context that {@link MonthlyBillResponse} lacks. */
public record MonthlyBillListResponse(
        Long id,
        Long contractId,
        Long roomId,
        String roomCode,
        Long branchId,
        String branchName,
        Integer billMonth,
        Integer billYear,
        BigDecimal rentAmount,
        BigDecimal totalExtraFee,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        PaymentStatus paymentStatus
) {
    public static MonthlyBillListResponse from(MonthlyBill bill) {
        var room = bill.getContract().getRoom();
        return new MonthlyBillListResponse(
                bill.getId(),
                bill.getContract().getId(),
                room.getId(),
                room.getRoomCode(),
                room.getBranch().getId(),
                room.getBranch().getName(),
                bill.getBillMonth(),
                bill.getBillYear(),
                bill.getRentAmount(),
                bill.getTotalExtraFee(),
                bill.getTotalAmount(),
                bill.getPaidAmount(),
                bill.getRemainingAmount(),
                bill.getPaymentStatus()
        );
    }
}
