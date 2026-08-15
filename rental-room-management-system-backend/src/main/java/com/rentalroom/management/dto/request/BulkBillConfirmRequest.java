package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkBillConfirmRequest(
        @NotEmpty List<Long> billIds
) {
}
