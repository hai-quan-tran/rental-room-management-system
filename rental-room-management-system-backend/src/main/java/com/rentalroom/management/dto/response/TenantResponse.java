package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Tenant;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TenantResponse(
        Long id,
        String fullName,
        LocalDate dateOfBirth,
        String idCardNumber,
        String phoneNumber,
        String email,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getFullName(),
                tenant.getDateOfBirth(),
                tenant.getIdCardNumber(),
                tenant.getPhoneNumber(),
                tenant.getEmail(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
