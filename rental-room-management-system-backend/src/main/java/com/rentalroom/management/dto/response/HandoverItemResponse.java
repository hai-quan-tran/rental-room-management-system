package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.RoomTypeHandoverItem;

import java.math.BigDecimal;

public record HandoverItemResponse(
        Long id,
        Long itemId,
        String itemName,
        BigDecimal itemPrice,
        Integer quantity,
        String note
) {
    public static HandoverItemResponse from(RoomTypeHandoverItem handoverItem) {
        return new HandoverItemResponse(
                handoverItem.getId(),
                handoverItem.getItem().getId(),
                handoverItem.getItem().getName(),
                handoverItem.getItem().getPrice(),
                handoverItem.getQuantity(),
                handoverItem.getNote()
        );
    }
}
