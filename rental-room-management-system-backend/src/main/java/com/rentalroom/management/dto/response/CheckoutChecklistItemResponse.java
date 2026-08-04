package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.CheckoutChecklistItem;

import java.math.BigDecimal;

public record CheckoutChecklistItemResponse(
        Long id,
        Long roomTypeHandoverItemId,
        String itemName,
        Integer totalQuantity,
        Integer damagedQuantity,
        Integer lostQuantity,
        Integer intactQuantity,
        BigDecimal deductionAmount,
        String note
) {
    public static CheckoutChecklistItemResponse from(CheckoutChecklistItem item) {
        return new CheckoutChecklistItemResponse(
                item.getId(),
                item.getRoomTypeHandoverItem().getId(),
                item.getRoomTypeHandoverItem().getItem().getName(),
                item.getTotalQuantity(),
                item.getDamagedQuantity(),
                item.getLostQuantity(),
                item.getIntactQuantity(),
                item.getDeductionAmount(),
                item.getNote()
        );
    }
}
