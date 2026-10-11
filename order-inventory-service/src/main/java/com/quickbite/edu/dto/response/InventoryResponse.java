package com.quickbite.edu.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryResponse {
    private Long id;
    private String sku;
    private String productName;
    private Integer availableQty;
    private Integer reservedQty;
    private LocalDateTime updatedAt;
}
