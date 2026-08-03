package com.rentalroom.management.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContractCreateRequest(
        @NotNull(message = "Ngày bắt đầu không được để trống") LocalDate startDate,

        /** Optional — planned end date of a fixed-term contract; left null when the term is undetermined. */
        LocalDate endDate,

        @NotNull(message = "Tiền cọc không được để trống")
        @DecimalMin(value = "0", message = "Tiền cọc không được âm")
        BigDecimal depositAmount,

        /** Optional — defaults to the room's default monthly rent when omitted. */
        @DecimalMin(value = "0", message = "Giá thuê không được âm")
        BigDecimal monthlyRent,

        @NotEmpty(message = "Hợp đồng phải có ít nhất 1 người thuê")
        @Valid
        List<ContractTenantRequest> tenants
) {
}
