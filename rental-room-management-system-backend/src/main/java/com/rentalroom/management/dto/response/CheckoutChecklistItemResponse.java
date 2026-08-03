package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.CheckoutChecklistItem;
import com.rentalroom.management.enums.ChecklistItemStatus;

import java.math.BigDecimal;

public record CheckoutChecklistItemResponse(
        Long id,
        Long roomTypeHandoverItemId,
        String itemName,
        ChecklistItemStatus status,
        BigDecimal deductionAmount,
        String note
) {
    public static CheckoutChecklistItemResponse from(CheckoutChecklistItem item) {
        return new CheckoutChecklistItemResponse(
                item.getId(),
                item.getRoomTypeHandoverItem().getId(),
                item.getRoomTypeHandoverItem().getItemName(),
                item.getStatus(),
                item.getDeductionAmount(),
                item.getNote()
        );
    }
}
