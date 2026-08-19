package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.dto.response.InvoiceReminderRunResponse;
import com.rentalroom.management.service.InvoiceReminderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual trigger for the invoice-reminder email job (normally runs on the 7th/8th/9th via cron) — for testing. */
@RestController
@RequestMapping("/api/admin/invoice-reminders")
@PreAuthorize("hasRole('ADMIN_TONG')")
public class InvoiceReminderController {

    private final InvoiceReminderService invoiceReminderService;

    public InvoiceReminderController(InvoiceReminderService invoiceReminderService) {
        this.invoiceReminderService = invoiceReminderService;
    }

    @PostMapping("/run")
    public ApiResponse<InvoiceReminderRunResponse> run() {
        return ApiResponse.success("Đã chạy tác vụ nhắc hóa đơn", invoiceReminderService.run());
    }
}
