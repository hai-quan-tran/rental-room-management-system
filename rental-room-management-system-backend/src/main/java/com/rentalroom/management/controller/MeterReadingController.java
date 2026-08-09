package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.dto.request.MeterReadingRequest;
import com.rentalroom.management.dto.response.MeterReadingGridRowResponse;
import com.rentalroom.management.dto.response.MeterReadingResponse;
import com.rentalroom.management.service.MeterReadingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Electricity/water meter readings — branch-scoped grid for a given month, per-room upsert. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class MeterReadingController {

    private final MeterReadingService meterReadingService;

    public MeterReadingController(MeterReadingService meterReadingService) {
        this.meterReadingService = meterReadingService;
    }

    @GetMapping("/api/branches/{branchId}/meter-readings")
    public ApiResponse<List<MeterReadingGridRowResponse>> listGrid(
            @PathVariable Long branchId,
            @RequestParam Integer billYear,
            @RequestParam Integer billMonth) {
        return ApiResponse.success(meterReadingService.listGrid(branchId, billYear, billMonth));
    }

    @PutMapping("/api/rooms/{roomId}/meter-readings")
    public ApiResponse<MeterReadingResponse> upsertReading(@PathVariable Long roomId, @Valid @RequestBody MeterReadingRequest request) {
        return ApiResponse.success("Lưu chỉ số thành công", meterReadingService.upsertReading(roomId, request));
    }
}
