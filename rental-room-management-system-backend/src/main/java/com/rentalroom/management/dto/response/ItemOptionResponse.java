package com.rentalroom.management.dto.response;

import com.rentalroom.management.entity.Item;

import java.math.BigDecimal;

/** Lightweight item lookup for the Room Type handover-item picker (auto-fills default quantity, validates stock). */
public record ItemOptionResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer quantityAvailable,
        Integer defaultQuantityPerRoom
) {
    public static ItemOptionResponse from(Item item) {
        return new ItemOptionResponse(item.getId(), item.getName(), item.getPrice(),
                item.getQuantityAvailable(), item.getDefaultQuantityPerRoom());
    }
}
