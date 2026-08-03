package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.NotNull;

public record ContractTenantRequest(
        @NotNull(message = "Người thuê không được để trống") Long tenantId,
        boolean representative
) {
}
