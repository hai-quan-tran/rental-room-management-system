package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Branch;

import java.time.LocalDateTime;

public record BranchResponse(
        Long id,
        String name,
        String address,
        Long managerAccountId,
        String managerFullName,
        String bankBin,
        String bankAccountNumber,
        String bankAccountName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getManagerAccount() != null ? branch.getManagerAccount().getId() : null,
                branch.getManagerAccount() != null ? branch.getManagerAccount().getFullName() : null,
                branch.getBankBin(),
                branch.getBankAccountNumber(),
                branch.getBankAccountName(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
