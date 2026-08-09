package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.MeterReading;
import com.rentalroom.management.enums.BillSyncStatus;

import java.math.BigDecimal;

public record MeterReadingResponse(
        Long id,
        Long roomId,
        Long extraFeeCategoryId,
        Integer billYear,
        Integer billMonth,
        BigDecimal oldReading,
        BigDecimal newReading,
        BigDecimal consumption,
        BigDecimal unitPrice,
        BigDecimal amount,
        BillSyncStatus billSyncStatus,
        String note
) {
    public static MeterReadingResponse from(MeterReading reading, BigDecimal unitPrice, BigDecimal amount, BillSyncStatus billSyncStatus) {
        return new MeterReadingResponse(
                reading.getId(),
                reading.getRoom().getId(),
                reading.getExtraFeeCategory().getId(),
                reading.getBillYear(),
                reading.getBillMonth(),
                reading.getOldReading(),
                reading.getNewReading(),
                reading.getConsumption(),
                unitPrice,
                amount,
                billSyncStatus,
                reading.getNote()
        );
    }
}
