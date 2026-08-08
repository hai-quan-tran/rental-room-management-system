package com.rentalroom.management.dto.response;

import java.util.List;

public record DashboardResponse(
        RoomStatusChartResponse roomStatus,
        List<MoveInOutPointResponse> moveInOut,
        List<RevenuePointResponse> revenue,
        List<OccupantsByBranchResponse> occupantsByBranch,
        List<MissingInvoiceRoomResponse> missingInvoiceRooms,
        List<UnpaidInvoiceRoomResponse> unpaidInvoiceRooms
) {
}
