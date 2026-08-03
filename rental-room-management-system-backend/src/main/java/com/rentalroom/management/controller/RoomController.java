package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.RoomRequest;
import com.rentalroom.management.dto.response.BranchOptionResponse;
import com.rentalroom.management.dto.response.RoomDetailResponse;
import com.rentalroom.management.dto.response.RoomResponse;
import com.rentalroom.management.enums.RoomStatus;
import com.rentalroom.management.service.RoomService;
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

/** Room management screen — ADMIN_TONG (all branches), ADMIN_CAP_1 (own branches only, spec 4.4). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/api/branches/{branchId}/rooms")
    public ApiResponse<PageResponse<RoomResponse>> list(
            @PathVariable Long branchId,
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(required = false) Long roomTypeId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(roomService.list(branchId, status, roomTypeId, search, pageable));
    }

    @GetMapping("/api/rooms/{id}")
    public ApiResponse<RoomDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success(roomService.get(id));
    }

    @GetMapping("/api/rooms/branch-options")
    public ApiResponse<List<BranchOptionResponse>> branchOptions() {
        return ApiResponse.success(roomService.branchOptions());
    }

    @PostMapping("/api/branches/{branchId}/rooms")
    @PreAuthorize("hasRole('ADMIN_TONG')")
    public ApiResponse<RoomResponse> create(@PathVariable Long branchId, @Valid @RequestBody RoomRequest request) {
        return ApiResponse.success("Tạo phòng thành công", roomService.create(branchId, request));
    }

    @PutMapping("/api/rooms/{id}")
    public ApiResponse<RoomResponse> update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return ApiResponse.success("Cập nhật phòng thành công", roomService.update(id, request));
    }

    @DeleteMapping("/api/rooms/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ApiResponse.success("Xóa phòng thành công", null);
    }
}
