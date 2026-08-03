package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.RoomTypeHandoverItem;

public record HandoverItemResponse(
        Long id,
        String itemName,
        Integer quantity,
        String note
) {
    public static HandoverItemResponse from(RoomTypeHandoverItem item) {
        return new HandoverItemResponse(item.getId(), item.getItemName(), item.getQuantity(), item.getNote());
    }
}
