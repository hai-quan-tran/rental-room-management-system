package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.RoomType;

import java.util.List;

public record RoomTypeResponse(
        Long id,
        String name,
        String area,
        String description,
        List<HandoverItemResponse> handoverItems
) {
    public static RoomTypeResponse from(RoomType roomType, List<HandoverItemResponse> items) {
        return new RoomTypeResponse(roomType.getId(), roomType.getName(), roomType.getArea(),
                roomType.getDescription(), items);
    }
}
