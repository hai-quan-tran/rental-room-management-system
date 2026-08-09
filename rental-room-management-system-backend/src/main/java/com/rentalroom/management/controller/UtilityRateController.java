package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.dto.request.UtilityRateRequest;
import com.rentalroom.management.dto.response.UtilityRateResponse;
import com.rentalroom.management.service.UtilityRateService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Utility (điện/nước) unit price, branch-scoped — both roles create/read within their own branch(es); add-only, no update/delete. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class UtilityRateController {

    private final UtilityRateService utilityRateService;

    public UtilityRateController(UtilityRateService utilityRateService) {
        this.utilityRateService = utilityRateService;
    }

    @GetMapping("/api/branches/{branchId}/utility-rates")
    public ApiResponse<List<UtilityRateResponse>> list(@PathVariable Long branchId) {
        return ApiResponse.success(utilityRateService.list(branchId));
    }

    @PostMapping("/api/branches/{branchId}/utility-rates")
    public ApiResponse<UtilityRateResponse> create(@PathVariable Long branchId, @Valid @RequestBody UtilityRateRequest request) {
        return ApiResponse.success("Tạo đơn giá thành công", utilityRateService.create(branchId, request));
    }
}
