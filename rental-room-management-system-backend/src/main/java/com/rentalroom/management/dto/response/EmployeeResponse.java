package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Employee;
import com.rentalroom.management.enums.Role;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String fullName,
        LocalDate dateOfBirth,
        String idCardNumber,
        String phoneNumber,
        String email,
        Long accountId,
        String username,
        Role role,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getFullName(),
                employee.getDateOfBirth(),
                employee.getIdCardNumber(),
                employee.getPhoneNumber(),
                employee.getEmail(),
                employee.getAccount().getId(),
                employee.getAccount().getUsername(),
                employee.getAccount().getRole(),
                employee.getAccount().isActive(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
