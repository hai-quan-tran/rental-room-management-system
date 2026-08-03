package com.rentalroom.management.dto.response;

public record RoomStatusChartResponse(
        long emptyCount,
        long occupiedCount
) {
}
