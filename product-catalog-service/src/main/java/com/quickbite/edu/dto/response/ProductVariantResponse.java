package com.quickbite.edu.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String sku;
    private BigDecimal price;
    private Integer stockQuantity;
    private String attributes;
    private String imageUrl;
    private LocalDateTime createdAt;
}