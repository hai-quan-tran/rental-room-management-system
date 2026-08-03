package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotNull;

public record ActiveStatusRequest(
        @NotNull Boolean active
) {
}
