package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.enums.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractResponse(
        Long id,
        Long roomId,
        String roomCode,
        Long branchId,
        String branchName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRent,
        BigDecimal depositAmount,
        ContractStatus status
) {
    public static ContractResponse from(Contract contract) {
        var room = contract.getRoom();
        return new ContractResponse(
                contract.getId(),
                room.getId(),
                room.getRoomCode(),
                room.getBranch().getId(),
                room.getBranch().getName(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getMonthlyRent(),
                contract.getDepositAmount(),
                contract.getStatus()
        );
    }
}
