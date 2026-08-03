package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.dto.request.CheckoutRequest;
import com.rentalroom.management.dto.response.CheckoutResponse;
import com.rentalroom.management.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Checkout / end-of-contract checklist — spec 4.4.6. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping("/api/contracts/{id}/checkout")
    public ApiResponse<CheckoutResponse> get(@PathVariable Long id) {
        return ApiResponse.success(checkoutService.get(id));
    }

    @PostMapping("/api/contracts/{id}/checkout")
    public ApiResponse<CheckoutResponse> checkout(@PathVariable Long id, @Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.success("Trả phòng thành công", checkoutService.checkout(id, request));
    }
}
