package com.rentalroom.management.common.util;

import java.math.BigDecimal;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class MoneyUtils {

    private MoneyUtils() {
    }

    /** Sums a stream of {@link BigDecimal} amounts, starting from {@link BigDecimal#ZERO}. */
    public static Collector<BigDecimal, ?, BigDecimal> summing() {
        return Collectors.reducing(BigDecimal.ZERO, BigDecimal::add);
    }
}
