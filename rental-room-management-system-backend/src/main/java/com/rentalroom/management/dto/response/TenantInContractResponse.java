package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.ContractTenant;

public record TenantInContractResponse(
        Long tenantId,
        String fullName,
        String idCardNumber,
        String phoneNumber,
        boolean representative
) {
    public static TenantInContractResponse from(ContractTenant contractTenant) {
        var tenant = contractTenant.getTenant();
        return new TenantInContractResponse(
                tenant.getId(),
                tenant.getFullName(),
                tenant.getIdCardNumber(),
                tenant.getPhoneNumber(),
                contractTenant.isRepresentative()
        );
    }
}
