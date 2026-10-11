package com.quickbite.edu.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    @NotBlank(message = "Mã SKU không được để trống")
    private String sku;

    @NotNull(message = "Giá biến thể không được để trống")
    @PositiveOrZero(message = "Giá biến thể phải lớn hơn hoặc bằng 0")
    private BigDecimal price;

    @NotNull(message = "Số lượng tồn kho không được để trống")
    @PositiveOrZero(message = "Số lượng tồn kho phải lớn hơn hoặc bằng 0")
    @Builder.Default
    private Integer stockQuantity = 0;

    private String attributes;

    private String imageUrl;
}