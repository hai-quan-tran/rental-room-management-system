package com.rentalroom.management.common.util;

import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;

import java.time.LocalDate;

public final class DateValidationUtils {

    private DateValidationUtils() {
    }

    /** No-op when {@code date} is null (optional dates skip the check). */
    public static void assertNotBefore(LocalDate date, LocalDate reference, String message) {
        if (date != null && date.isBefore(reference)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }
}
