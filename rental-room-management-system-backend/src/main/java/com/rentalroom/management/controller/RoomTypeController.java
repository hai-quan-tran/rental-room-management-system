package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.HandoverItemRequest;
import com.rentalroom.management.dto.request.RoomTypeRequest;
import com.rentalroom.management.dto.response.HandoverItemResponse;
import com.rentalroom.management.dto.response.RoomTypeResponse;
import com.rentalroom.management.service.RoomTypeService;
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

import java.util.List;

/** Room type management — ADMIN_TONG manages, both roles read (used to build room dropdowns). */
@RestController
@RequestMapping("/api/room-types")
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    public RoomTypeController(RoomTypeService roomTypeService) {
        this.roomTypeService = roomTypeService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RoomTypeResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(roomTypeService.list(search, pageable));
    }

    @GetMapping("/all")
    public ApiResponse<List<RoomTypeResponse>> listAll() {
        return ApiResponse.success(roomTypeService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomTypeResponse> get(@PathVariable Long id) {
        return ApiResponse.success(roomTypeService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_TONG')")
    public ApiResponse<RoomTypeResponse> create(@Valid @RequestBody RoomTypeRequest request) {
        return ApiResponse.success("Tạo loại phòng thành công", roomTypeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TONG')")
    public ApiResponse<RoomTypeResponse> update(@PathVariable Long id, @Valid @RequestBody RoomTypeRequest request) {
        return ApiResponse.success("Cập nhật loại phòng thành công", roomTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TONG')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roomTypeService.delete(id);
        return ApiResponse.success("Xóa loại phòng thành công", null);
    }

    @PutMapping("/{id}/handover-items")
    @PreAuthorize("hasRole('ADMIN_TONG')")
    public ApiResponse<List<HandoverItemResponse>> replaceHandoverItems(
            @PathVariable Long id, @Valid @RequestBody List<HandoverItemRequest> items) {
        return ApiResponse.success("Cập nhật vật dụng bàn giao thành công", roomTypeService.replaceHandoverItems(id, items));
    }
}
