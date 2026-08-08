package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.dto.response.DashboardResponse;
import com.rentalroom.management.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard charts — spec 4.5. ADMIN_TONG may filter by branch; ADMIN_CAP_1 is always scoped to its own branches. */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> get(@RequestParam(required = false) Long branchId,
                                               @RequestParam(required = false) Integer fromYear,
                                               @RequestParam(required = false) Integer fromMonth,
                                               @RequestParam(required = false) Integer toYear,
                                               @RequestParam(required = false) Integer toMonth) {
        return ApiResponse.success(dashboardService.getDashboard(branchId, fromYear, fromMonth, toYear, toMonth));
    }
}
