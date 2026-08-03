package com.rentalroom.management.dto.response;

import java.util.List;

public record BranchDetailResponse(
        BranchResponse branch,
        long totalRooms,
        List<RoomTypeSummaryResponse> roomTypeSummaries
) {
    public static BranchDetailResponse of(BranchResponse branch, List<RoomTypeSummaryResponse> summaries) {
        long total = summaries.stream().mapToLong(RoomTypeSummaryResponse::roomCount).sum();
        return new BranchDetailResponse(branch, total, summaries);
    }
}
