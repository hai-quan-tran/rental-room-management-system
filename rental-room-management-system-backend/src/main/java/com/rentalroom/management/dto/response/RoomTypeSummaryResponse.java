package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.BranchRoomSummary;

public record RoomTypeSummaryResponse(
        Long roomTypeId,
        String roomTypeName,
        long roomCount,
        long emptyRoomCount,
        long occupiedRoomCount
) {
    public static RoomTypeSummaryResponse from(BranchRoomSummary summary) {
        return new RoomTypeSummaryResponse(
                summary.getId().getRoomTypeId(),
                summary.getRoomTypeName(),
                summary.getRoomCount(),
                summary.getEmptyRoomCount(),
                summary.getOccupiedRoomCount()
        );
    }
}
