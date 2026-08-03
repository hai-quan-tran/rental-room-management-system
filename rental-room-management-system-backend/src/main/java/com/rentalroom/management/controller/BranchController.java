package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.BranchRequest;
import com.rentalroom.management.dto.response.BranchDetailResponse;
import com.rentalroom.management.dto.response.BranchResponse;
import com.rentalroom.management.service.BranchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Branch management screen — ADMIN_TONG only. */
@RestController
@RequestMapping("/api/branches")
@PreAuthorize("hasRole('ADMIN_TONG')")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public ApiResponse<PageResponse<BranchResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(branchService.list(search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<BranchDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success(branchService.get(id));
    }

    @PostMapping
    public ApiResponse<BranchResponse> create(@Valid @RequestBody BranchRequest request) {
        return ApiResponse.success("Tạo chi nhánh thành công", branchService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BranchResponse> update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return ApiResponse.success("Cập nhật chi nhánh thành công", branchService.update(id, request));
    }
}
