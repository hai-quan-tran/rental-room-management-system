package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.dto.request.ExtraFeeCategoryRequest;
import com.rentalroom.management.dto.response.ExtraFeeCategoryResponse;
import com.rentalroom.management.service.ExtraFeeCategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Extra fee category lookup (Điện, Nước, Gửi xe, ...) — ADMIN_TONG manages, both roles read. */
@RestController
@RequestMapping("/api/extra-fee-categories")
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class ExtraFeeCategoryController {

    private final ExtraFeeCategoryService extraFeeCategoryService;

    public ExtraFeeCategoryController(ExtraFeeCategoryService extraFeeCategoryService) {
        this.extraFeeCategoryService = extraFeeCategoryService;
    }

    @GetMapping
    public ApiResponse<List<ExtraFeeCategoryResponse>> listAll() {
        return ApiResponse.success(extraFeeCategoryService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_TONG')")
    public ApiResponse<ExtraFeeCategoryResponse> create(@Valid @RequestBody ExtraFeeCategoryRequest request) {
        return ApiResponse.success("Tạo danh mục thành công", extraFeeCategoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TONG')")
    public ApiResponse<ExtraFeeCategoryResponse> update(@PathVariable Long id, @Valid @RequestBody ExtraFeeCategoryRequest request) {
        return ApiResponse.success("Cập nhật danh mục thành công", extraFeeCategoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TONG')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        extraFeeCategoryService.delete(id);
        return ApiResponse.success("Xóa danh mục thành công", null);
    }
}
