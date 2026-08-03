package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Branch;

/** Minimal branch lookup for pickers (e.g. the Rooms screen's branch selector) — both roles can read this,
 * unlike the full {@link BranchResponse} which is gated to ADMIN_TONG. */
public record BranchOptionResponse(
        Long id,
        String name
) {
    public static BranchOptionResponse from(Branch branch) {
        return new BranchOptionResponse(branch.getId(), branch.getName());
    }
}
