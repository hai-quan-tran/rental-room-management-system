package com.rentalroom.management.dto.response;

public record MoveInOutPointResponse(
        int year,
        int month,
        long moveInCount,
        long moveOutCount
) {
}
