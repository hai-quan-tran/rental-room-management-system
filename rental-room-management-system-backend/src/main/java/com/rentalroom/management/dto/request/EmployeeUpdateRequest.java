package com.rentalroom.management.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/** Personal info only — account credentials (username/password/role) are managed via the Account endpoints. */
public record EmployeeUpdateRequest(
        @NotBlank(message = "Họ tên không được để trống") String fullName,

        @NotNull(message = "Ngày sinh không được để trống")
        @Past(message = "Ngày sinh phải trong quá khứ")
        LocalDate dateOfBirth,

        @NotBlank(message = "CCCD không được để trống")
        @Pattern(regexp = "\\d{9}(\\d{3})?", message = "CCCD phải gồm 9 hoặc 12 chữ số")
        String idCardNumber,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "0(3|5|7|8|9)\\d{8}", message = "Số điện thoại không đúng định dạng Việt Nam")
        String phoneNumber,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email
) {
}
