package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.CollectDebtRequest;
import com.rentalroom.management.dto.response.DebtRecordResponse;
import com.rentalroom.management.enums.DebtStatus;
import com.rentalroom.management.service.DebtRecordService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Debt records left over after a checkout exceeds the deposit — spec 4.4.6. */
@RestController
@RequestMapping("/api/debt-records")
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class DebtRecordController {

    private final DebtRecordService debtRecordService;

    public DebtRecordController(DebtRecordService debtRecordService) {
        this.debtRecordService = debtRecordService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DebtRecordResponse>> list(
            @RequestParam(required = false) DebtStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(debtRecordService.list(status, pageable));
    }

    @PatchMapping("/{id}/collect")
    public ApiResponse<DebtRecordResponse> collect(@PathVariable Long id, @Valid @RequestBody CollectDebtRequest request) {
        return ApiResponse.success("Ghi nhận thu công nợ thành công", debtRecordService.collect(id, request.collectedAmount()));
    }
}
