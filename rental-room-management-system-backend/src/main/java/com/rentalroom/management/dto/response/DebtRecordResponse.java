package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.DebtRecord;
import com.rentalroom.management.enums.DebtStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DebtRecordResponse(
        Long id,
        Long contractId,
        Long checklistId,
        Long roomId,
        String roomCode,
        String branchName,
        BigDecimal amount,
        String reason,
        DebtStatus status,
        BigDecimal collectedAmount,
        String note,
        LocalDateTime createdAt
) {
    public static DebtRecordResponse from(DebtRecord record) {
        var room = record.getContract().getRoom();
        return new DebtRecordResponse(
                record.getId(),
                record.getContract().getId(),
                record.getChecklist() != null ? record.getChecklist().getId() : null,
                room.getId(),
                room.getRoomCode(),
                room.getBranch().getName(),
                record.getAmount(),
                record.getReason(),
                record.getStatus(),
                record.getCollectedAmount(),
                record.getNote(),
                record.getCreatedAt()
        );
    }
}
