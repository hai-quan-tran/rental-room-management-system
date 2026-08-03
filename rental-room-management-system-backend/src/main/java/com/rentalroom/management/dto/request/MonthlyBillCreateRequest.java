package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MonthlyBillCreateRequest(
        @NotNull @Min(1) @Max(12) Integer billMonth,
        @NotNull @Min(2000) @Max(2100) Integer billYear
) {
}
