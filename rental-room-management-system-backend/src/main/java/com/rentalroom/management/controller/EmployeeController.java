package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.EmployeeCreateRequest;
import com.rentalroom.management.dto.request.EmployeeUpdateRequest;
import com.rentalroom.management.dto.response.EmployeeResponse;
import com.rentalroom.management.service.EmployeeService;
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

/** Employee management screen — ADMIN_TONG only. */
@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasRole('ADMIN_TONG')")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public ApiResponse<PageResponse<EmployeeResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(employeeService.list(search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<EmployeeResponse> get(@PathVariable Long id) {
        return ApiResponse.success(employeeService.get(id));
    }

    @PostMapping
    public ApiResponse<EmployeeResponse> create(@Valid @RequestBody EmployeeCreateRequest request) {
        return ApiResponse.success("Tạo nhân viên thành công", employeeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<EmployeeResponse> update(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateRequest request) {
        return ApiResponse.success("Cập nhật nhân viên thành công", employeeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ApiResponse.success("Xóa nhân viên thành công", null);
    }
}
