package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.TenantRequest;
import com.rentalroom.management.dto.response.TenantRentalHistoryResponse;
import com.rentalroom.management.dto.response.TenantResponse;
import com.rentalroom.management.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tenant management screen — ADMIN_TONG and ADMIN_CAP_1 (spec 4.2). */
@RestController
@RequestMapping("/api/tenants")
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TenantResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(tenantService.list(search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantResponse> get(@PathVariable Long id) {
        return ApiResponse.success(tenantService.get(id));
    }

    @GetMapping("/{id}/rental-history")
    public ApiResponse<List<TenantRentalHistoryResponse>> rentalHistory(@PathVariable Long id) {
        return ApiResponse.success(tenantService.getRentalHistory(id));
    }

    @PostMapping
    public ApiResponse<TenantResponse> create(@Valid @RequestBody TenantRequest request) {
        return ApiResponse.success("Tạo người thuê thành công", tenantService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TenantResponse> update(@PathVariable Long id, @Valid @RequestBody TenantRequest request) {
        return ApiResponse.success("Cập nhật người thuê thành công", tenantService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return ApiResponse.success("Xóa người thuê thành công", null);
    }
}
