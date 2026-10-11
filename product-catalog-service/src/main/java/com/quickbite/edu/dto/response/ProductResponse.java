package com.quickbite.edu.dto.response;

import com.quickbite.edu.entity.enums.ProductStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private Long sellerId;
    private ProductStatus status;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private String images;
    private List<ProductVariantResponse> variants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
