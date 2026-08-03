package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.BulkMonthlyBillCreateRequest;
import com.rentalroom.management.dto.request.ExtraFeeItemRequest;
import com.rentalroom.management.dto.request.MonthlyBillCreateRequest;
import com.rentalroom.management.dto.request.PaymentRequest;
import com.rentalroom.management.dto.response.BulkMonthlyBillCreateResponse;
import com.rentalroom.management.dto.response.ExtraFeeItemResponse;
import com.rentalroom.management.dto.response.MonthlyBillDetailResponse;
import com.rentalroom.management.dto.response.MonthlyBillListResponse;
import com.rentalroom.management.dto.response.MonthlyBillResponse;
import com.rentalroom.management.service.BillingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Monthly bill / extra fee / payment — tab 4-5 of the room screen (spec 4.4.4/4.4.5). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/api/contracts/{contractId}/monthly-bills")
    public ApiResponse<List<MonthlyBillResponse>> listByContract(@PathVariable Long contractId) {
        return ApiResponse.success(billingService.listByContract(contractId));
    }

    /** Cross-room bill listing for the "Hóa đơn hằng tháng của các phòng" screen — view + record payment only. */
    @GetMapping("/api/monthly-bills")
    public ApiResponse<PageResponse<MonthlyBillListResponse>> list(
            @RequestParam Integer billYear,
            @RequestParam Integer billMonth,
            @RequestParam(required = false) Long branchId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(billingService.listAll(billYear, billMonth, branchId, pageable));
    }

    @PostMapping("/api/contracts/{contractId}/monthly-bills")
    public ApiResponse<MonthlyBillResponse> createBill(@PathVariable Long contractId, @Valid @RequestBody MonthlyBillCreateRequest request) {
        return ApiResponse.success("Tạo hóa đơn tháng thành công", billingService.createBill(contractId, request));
    }

    /** Bulk-create the rent-only bill shell for every room with an active contract in scope — "Tạo hóa đơn hàng loạt". */
    @PostMapping("/api/monthly-bills/bulk")
    public ApiResponse<BulkMonthlyBillCreateResponse> bulkCreate(@Valid @RequestBody BulkMonthlyBillCreateRequest request) {
        return ApiResponse.success("Tạo hóa đơn hàng loạt thành công",
                billingService.bulkCreate(request.billYear(), request.billMonth(), request.branchId()));
    }

    @GetMapping("/api/monthly-bills/{id}")
    public ApiResponse<MonthlyBillDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success(billingService.get(id));
    }

    @PostMapping("/api/monthly-bills/{id}/extra-fee-items")
    public ApiResponse<ExtraFeeItemResponse> addExtraFeeItem(@PathVariable Long id, @Valid @RequestBody ExtraFeeItemRequest request) {
        return ApiResponse.success("Thêm chi phí phát sinh thành công", billingService.addExtraFeeItem(id, request));
    }

    @DeleteMapping("/api/monthly-bills/{id}/extra-fee-items/{itemId}")
    public ApiResponse<Void> deleteExtraFeeItem(@PathVariable Long id, @PathVariable Long itemId) {
        billingService.deleteExtraFeeItem(id, itemId);
        return ApiResponse.success("Xóa chi phí phát sinh thành công", null);
    }

    @PostMapping("/api/monthly-bills/{id}/payments")
    public ApiResponse<MonthlyBillResponse> recordPayment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return ApiResponse.success("Ghi nhận thanh toán thành công", billingService.recordPayment(id, request));
    }
}
