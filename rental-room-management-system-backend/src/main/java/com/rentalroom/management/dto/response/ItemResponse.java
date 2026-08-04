package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Item;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemResponse(
        Long id,
        Long branchId,
        String branchName,
        String name,
        BigDecimal price,
        Integer quantityAvailable,
        Integer defaultQuantityPerRoom,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getBranch().getId(),
                item.getBranch().getName(),
                item.getName(),
                item.getPrice(),
                item.getQuantityAvailable(),
                item.getDefaultQuantityPerRoom(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
