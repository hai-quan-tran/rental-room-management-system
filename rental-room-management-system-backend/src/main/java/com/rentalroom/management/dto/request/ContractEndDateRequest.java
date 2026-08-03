package com.rentalroom.management.dto.request;

import java.time.LocalDate;

/** Null clears the end date back to "undetermined" — only null itself is meaningful, so no @NotNull. */
public record ContractEndDateRequest(
        LocalDate endDate
) {
}
