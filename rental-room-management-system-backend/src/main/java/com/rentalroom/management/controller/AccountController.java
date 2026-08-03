package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.AccountCreateRequest;
import com.rentalroom.management.dto.request.AccountUpdateRequest;
import com.rentalroom.management.dto.request.ActiveStatusRequest;
import com.rentalroom.management.dto.request.ChangePasswordRequest;
import com.rentalroom.management.dto.response.AccountResponse;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Account management screen — ADMIN_TONG only (spec 4.1). */
@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('ADMIN_TONG')")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AccountResponse>> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(accountService.list(role, active, search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AccountResponse> get(@PathVariable Long id) {
        return ApiResponse.success(accountService.get(id));
    }

    @PostMapping
    public ApiResponse<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request) {
        return ApiResponse.success("Tạo tài khoản thành công", accountService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountResponse> update(@PathVariable Long id, @Valid @RequestBody AccountUpdateRequest request) {
        return ApiResponse.success("Cập nhật tài khoản thành công", accountService.update(id, request));
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<Void> changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(id, request.newPassword());
        return ApiResponse.success("Đổi mật khẩu thành công", null);
    }

    @PatchMapping("/{id}/active")
    public ApiResponse<AccountResponse> setActive(@PathVariable Long id, @Valid @RequestBody ActiveStatusRequest request) {
        return ApiResponse.success(accountService.setActive(id, request.active()));
    }
}
