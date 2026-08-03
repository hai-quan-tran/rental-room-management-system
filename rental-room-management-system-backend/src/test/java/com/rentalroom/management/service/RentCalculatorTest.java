package com.rentalroom.management.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RentCalculatorTest {

    @Test
    void fullMonth_contractStartsBeforeMonthAndHasNoEndDate_returnsFullRent() {
        BigDecimal rent = RentCalculator.calculateProratedRent(
                new BigDecimal("2800000"), 2026, 3,
                LocalDate.of(2026, 1, 1), null);

        assertEquals(new BigDecimal("2800000"), rent);
    }

    @Test
    void partialMonth_nonLeapFebruary_matchesSpecExample() {
        // Spec example: start 15/2/2026 (non-leap, 28 days) -> 14 days occupied (15..28 inclusive)
        BigDecimal rent = RentCalculator.calculateProratedRent(
                new BigDecimal("2800000"), 2026, 2,
                LocalDate.of(2026, 2, 15), null);

        assertEquals(new BigDecimal("1400000"), rent);
    }

    @Test
    void partialMonth_leapFebruary_usesActualDaysInMonth() {
        // 2028 is a leap year -> February has 29 days, not 28
        BigDecimal rent = RentCalculator.calculateProratedRent(
                new BigDecimal("2900000"), 2028, 2,
                LocalDate.of(2028, 2, 15), null);

        // 15 days occupied (15..29 inclusive) out of 29 days -> exactly 1,500,000
        assertEquals(new BigDecimal("1500000"), rent);
    }

    @Test
    void partialMonth_contractEndsMidMonth_prorateByRemainingDays() {
        BigDecimal rent = RentCalculator.calculateProratedRent(
                new BigDecimal("3000000"), 2026, 6,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 10));

        // June has 30 days; occupied 1..10 inclusive = 10 days
        assertEquals(new BigDecimal("1000000"), rent);
    }

    @Test
    void shortStay_startsAndEndsWithinSameMonth() {
        BigDecimal rent = RentCalculator.calculateProratedRent(
                new BigDecimal("3100000"), 2026, 7,
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20));

        // July has 31 days; occupied 10..20 inclusive = 11 days
        assertEquals(new BigDecimal("1100000"), rent);
    }

    @Test
    void contractDoesNotOverlapMonth_returnsZero() {
        BigDecimal rent = RentCalculator.calculateProratedRent(
                new BigDecimal("3000000"), 2026, 5,
                LocalDate.of(2026, 6, 1), null);

        assertEquals(BigDecimal.ZERO, rent);
    }
}
