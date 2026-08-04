package com.rentalroom.management.controller;

import com.rentalroom.management.common.ApiResponse;
import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.ItemRequest;
import com.rentalroom.management.dto.response.ItemOptionResponse;
import com.rentalroom.management.dto.response.ItemResponse;
import com.rentalroom.management.service.ItemService;
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

/** Item (vật dụng) management — branch-scoped, both roles create/edit/delete within their own branch(es). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/api/branches/{branchId}/items")
    public ApiResponse<PageResponse<ItemResponse>> list(
            @PathVariable Long branchId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(itemService.list(branchId, search, pageable));
    }

    @GetMapping("/api/branches/{branchId}/items/all")
    public ApiResponse<List<ItemOptionResponse>> listAll(@PathVariable Long branchId) {
        return ApiResponse.success(itemService.listAll(branchId));
    }

    @GetMapping("/api/items/{id}")
    public ApiResponse<ItemResponse> get(@PathVariable Long id) {
        return ApiResponse.success(itemService.get(id));
    }

    @PostMapping("/api/branches/{branchId}/items")
    public ApiResponse<ItemResponse> create(@PathVariable Long branchId, @Valid @RequestBody ItemRequest request) {
        return ApiResponse.success("Tạo vật dụng thành công", itemService.create(branchId, request));
    }

    @PutMapping("/api/items/{id}")
    public ApiResponse<ItemResponse> update(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return ApiResponse.success("Cập nhật vật dụng thành công", itemService.update(id, request));
    }

    @DeleteMapping("/api/items/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        itemService.delete(id);
        return ApiResponse.success("Xóa vật dụng thành công", null);
    }
}
