package com.rentalroom.management.security;

import com.rentalroom.management.enums.Role;

import java.util.List;

/**
 * Authenticated identity extracted from the JWT access token claims
 * (userId, username, role, managed branchIds — see JWT payload contract in the project spec).
 */
public record UserPrincipal(
        Long userId,
        String username,
        Role role,
        List<Long> branchIds
) {
}
