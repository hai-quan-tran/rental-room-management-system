package com.rentalroom.management.dto.response;

import java.util.List;

public record ContractDetailResponse(
        ContractResponse contract,
        List<TenantInContractResponse> tenants
) {
}
