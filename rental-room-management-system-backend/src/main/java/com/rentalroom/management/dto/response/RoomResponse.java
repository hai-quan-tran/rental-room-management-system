package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Room;
import com.rentalroom.management.enums.RoomStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RoomResponse(
        Long id,
        Long branchId,
        String branchName,
        String roomCode,
        Long roomTypeId,
        String roomTypeName,
        BigDecimal monthlyRent,
        RoomStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getBranch().getId(),
                room.getBranch().getName(),
                room.getRoomCode(),
                room.getRoomType().getId(),
                room.getRoomType().getName(),
                room.getMonthlyRent(),
                room.getStatus(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
