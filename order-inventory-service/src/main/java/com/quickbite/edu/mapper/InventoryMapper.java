package com.quickbite.edu.mapper;

import com.quickbite.edu.dto.response.InventoryResponse;
import com.quickbite.edu.entity.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryResponse toResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .sku(inventory.getSku())
                .productName(inventory.getProductName())
                .availableQty(inventory.getAvailableQty())
                .reservedQty(inventory.getReservedQty())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
