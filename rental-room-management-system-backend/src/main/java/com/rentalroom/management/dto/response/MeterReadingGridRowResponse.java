package com.rentalroom.management.dto.response;

import java.util.List;

/** 1 room's row in the "Nhập chỉ số điện nước" grid — 1 cell per metered category. */
public record MeterReadingGridRowResponse(
        Long roomId,
        String roomCode,
        Long contractId,
        List<MeterReadingCellResponse> readings
) {
}
