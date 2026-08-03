package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Account;
import com.rentalroom.management.enums.Role;

import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String fullName,
        String username,
        Role role,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getFullName(),
                account.getUsername(),
                account.getRole(),
                account.isActive(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
