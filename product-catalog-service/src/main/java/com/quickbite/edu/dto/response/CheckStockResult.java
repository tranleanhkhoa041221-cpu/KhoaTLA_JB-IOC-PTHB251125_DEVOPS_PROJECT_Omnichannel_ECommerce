package com.quickbite.edu.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckStockResult {
    private boolean available;
    private BigDecimal price;
    private String productName;
    private String sku;
    private Integer stockQuantity;
    private String message;
}
