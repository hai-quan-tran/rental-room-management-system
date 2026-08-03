package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.dto.request.ContractCreateRequest;
import com.rentalroom.management.dto.request.ContractEndDateRequest;
import com.rentalroom.management.dto.request.ContractTenantRequest;
import com.rentalroom.management.dto.response.ContractDetailResponse;
import com.rentalroom.management.dto.response.ContractResponse;
import com.rentalroom.management.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contract lifecycle — ADMIN_TONG (all), ADMIN_CAP_1 (own branches only, spec 4.4.3/4.4.7). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping("/api/rooms/{roomId}/contracts")
    public ApiResponse<List<ContractResponse>> listByRoom(@PathVariable Long roomId) {
        return ApiResponse.success(contractService.listByRoom(roomId));
    }

    @GetMapping("/api/contracts/{id}")
    public ApiResponse<ContractDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success(contractService.get(id));
    }

    @PostMapping("/api/rooms/{roomId}/contracts")
    public ApiResponse<ContractDetailResponse> create(@PathVariable Long roomId, @Valid @RequestBody ContractCreateRequest request) {
        return ApiResponse.success("Tạo hợp đồng thành công", contractService.create(roomId, request));
    }

    @PutMapping("/api/contracts/{id}/end-date")
    public ApiResponse<ContractDetailResponse> updateEndDate(@PathVariable Long id, @Valid @RequestBody ContractEndDateRequest request) {
        return ApiResponse.success("Cập nhật ngày kết thúc thành công", contractService.updateEndDate(id, request));
    }

    @PostMapping("/api/contracts/{id}/tenants")
    public ApiResponse<ContractDetailResponse> addTenant(@PathVariable Long id, @Valid @RequestBody ContractTenantRequest request) {
        return ApiResponse.success("Thêm người thuê thành công", contractService.addTenant(id, request));
    }

    @DeleteMapping("/api/contracts/{id}/tenants/{tenantId}")
    public ApiResponse<ContractDetailResponse> removeTenant(@PathVariable Long id, @PathVariable Long tenantId) {
        return ApiResponse.success("Xóa người thuê khỏi hợp đồng thành công", contractService.removeTenant(id, tenantId));
    }
}
