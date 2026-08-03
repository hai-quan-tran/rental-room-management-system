package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.CheckoutChecklist;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CheckoutResponse(
        Long checklistId,
        Long contractId,
        LocalDateTime checkedAt,
        BigDecimal depositAmount,
        BigDecimal deductionAmount,
        BigDecimal depositRefundAmount,
        String note,
        List<CheckoutChecklistItemResponse> items,
        DebtRecordResponse debtRecord
) {
    public static CheckoutResponse of(CheckoutChecklist checklist, DebtRecordResponse debtRecord) {
        List<CheckoutChecklistItemResponse> items = checklist.getItems().stream()
                .map(CheckoutChecklistItemResponse::from)
                .toList();
        return new CheckoutResponse(
                checklist.getId(),
                checklist.getContract().getId(),
                checklist.getCheckedAt(),
                checklist.getDepositAmount(),
                checklist.getDeductionAmount(),
                checklist.getDepositRefundAmount(),
                checklist.getNote(),
                items,
                debtRecord
        );
    }
}
