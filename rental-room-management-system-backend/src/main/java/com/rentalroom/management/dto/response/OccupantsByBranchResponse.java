package com.rentalroom.management.dto.response;

public record OccupantsByBranchResponse(
        long branchId,
        String branchName,
        long occupantCount
) {
}
