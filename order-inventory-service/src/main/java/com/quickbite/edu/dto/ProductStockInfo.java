package com.quickbite.edu.dto;

import lombok.*;

import java.math.BigDecimal;

/** Ket qua tra ve tu Product Catalog Service qua gRPC checkStockAndPrice(). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductStockInfo {
    private boolean available;
    private BigDecimal price;
    private String productName;
    private String message;
}
