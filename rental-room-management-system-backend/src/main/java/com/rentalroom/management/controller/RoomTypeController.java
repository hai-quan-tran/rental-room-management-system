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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Room type management — each room type belongs to exactly 1 branch; both roles manage their own branch's types. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    public RoomTypeController(RoomTypeService roomTypeService) {
        this.roomTypeService = roomTypeService;
    }

    @GetMapping("/api/branches/{branchId}/room-types")
    public ApiResponse<PageResponse<RoomTypeResponse>> list(
            @PathVariable Long branchId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(roomTypeService.list(branchId, search, pageable));
    }

    @GetMapping("/api/branches/{branchId}/room-types/all")
    public ApiResponse<List<RoomTypeResponse>> listAll(@PathVariable Long branchId) {
        return ApiResponse.success(roomTypeService.listAll(branchId));
    }

    @GetMapping("/api/room-types/{id}")
    public ApiResponse<RoomTypeResponse> get(@PathVariable Long id) {
        return ApiResponse.success(roomTypeService.get(id));
    }

    @PostMapping("/api/branches/{branchId}/room-types")
    public ApiResponse<RoomTypeResponse> create(@PathVariable Long branchId, @Valid @RequestBody RoomTypeRequest request) {
        return ApiResponse.success("Tạo loại phòng thành công", roomTypeService.create(branchId, request));
    }

    @PutMapping("/api/room-types/{id}")
    public ApiResponse<RoomTypeResponse> update(@PathVariable Long id, @Valid @RequestBody RoomTypeRequest request) {
        return ApiResponse.success("Cập nhật loại phòng thành công", roomTypeService.update(id, request));
    }

    @DeleteMapping("/api/room-types/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roomTypeService.delete(id);
        return ApiResponse.success("Xóa loại phòng thành công", null);
    }

    @PutMapping("/api/room-types/{id}/handover-items")
    public ApiResponse<List<HandoverItemResponse>> replaceHandoverItems(
            @PathVariable Long id, @Valid @RequestBody List<HandoverItemRequest> items) {
        return ApiResponse.success("Cập nhật vật dụng bàn giao thành công", roomTypeService.replaceHandoverItems(id, items));
    }
}
