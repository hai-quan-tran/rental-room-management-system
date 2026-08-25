package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.dto.response.TenantBillReminderRunResponse;
import com.rentalroom.management.service.TenantBillReminderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual trigger for the tenant-bill-reminder email job (normally runs on the 11th via cron) — for testing. */
@RestController
@RequestMapping("/api/admin/tenant-bill-reminders")
@PreAuthorize("hasRole('ADMIN_TONG')")
public class TenantBillReminderController {

    private final TenantBillReminderService tenantBillReminderService;

    public TenantBillReminderController(TenantBillReminderService tenantBillReminderService) {
        this.tenantBillReminderService = tenantBillReminderService;
    }

    @PostMapping("/run")
    public ApiResponse<TenantBillReminderRunResponse> run() {
        return ApiResponse.success("Đã chạy tác vụ nhắc thanh toán hóa đơn", tenantBillReminderService.run());
    }
}
