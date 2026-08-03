package com.rentalroom.management.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Prorated monthly rent per spec: full {@code monthlyRent} when the tenancy covers the whole
 * calendar month, otherwise {@code monthlyRent / actualDaysInMonth * actualDaysOccupied} — using
 * the real number of days in that specific month (28/29/30/31), never a flat 30-day assumption.
 */
public final class RentCalculator {

    private RentCalculator() {
    }

    /**
     * @param monthlyRent  the contract's monthly rent
     * @param year         bill year
     * @param month        bill month (1-12)
     * @param contractStart contract start date
     * @param contractEnd  contract end date (last day occupied, inclusive), or null if still active
     */
    public static BigDecimal calculateProratedRent(BigDecimal monthlyRent, int year, int month,
                                                    LocalDate contractStart, LocalDate contractEnd) {
        YearMonth yearMonth = YearMonth.of(year, month);
        int daysInMonth = yearMonth.lengthOfMonth();
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        LocalDate periodStart = contractStart.isAfter(monthStart) ? contractStart : monthStart;
        LocalDate periodEnd = (contractEnd != null && contractEnd.isBefore(monthEnd)) ? contractEnd : monthEnd;

        if (periodStart.isAfter(periodEnd)) {
            return BigDecimal.ZERO;
        }

        long daysOccupied = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        if (daysOccupied >= daysInMonth) {
            return monthlyRent.setScale(0, RoundingMode.HALF_UP);
        }
        return monthlyRent.multiply(BigDecimal.valueOf(daysOccupied))
                .divide(BigDecimal.valueOf(daysInMonth), 0, RoundingMode.HALF_UP);
    }
}
