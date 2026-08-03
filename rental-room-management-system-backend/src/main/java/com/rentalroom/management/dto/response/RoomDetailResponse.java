package com.rentalroom.management.dto.response;

import java.util.List;

public record RoomDetailResponse(
        RoomResponse room,
        List<HandoverItemResponse> handoverItems
) {
}
