package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.ContractTenant;
import com.rentalroom.management.enums.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TenantRentalHistoryResponse(
        Long contractId,
        String branchName,
        String roomCode,
        LocalDate startDate,
        LocalDate endDate,
        ContractStatus status,
        BigDecimal depositAmount,
        boolean representative
) {
    public static TenantRentalHistoryResponse from(ContractTenant contractTenant) {
        var contract = contractTenant.getContract();
        var room = contract.getRoom();
        return new TenantRentalHistoryResponse(
                contract.getId(),
                room.getBranch().getName(),
                room.getRoomCode(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getStatus(),
                contract.getDepositAmount(),
                contractTenant.isRepresentative()
        );
    }
}
