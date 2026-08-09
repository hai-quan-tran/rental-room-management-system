package com.rentalroom.management.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class MoneyUtils {

    private MoneyUtils() {
    }

    /** Sums a stream of {@link BigDecimal} amounts, starting from {@link BigDecimal#ZERO}. */
    public static Collector<BigDecimal, ?, BigDecimal> summing() {
        return Collectors.reducing(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Rounds to a whole VND amount (every money column in this schema is {@code DECIMAL(15,0)}). */
    public static BigDecimal roundToWholeVnd(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP);
    }
}
