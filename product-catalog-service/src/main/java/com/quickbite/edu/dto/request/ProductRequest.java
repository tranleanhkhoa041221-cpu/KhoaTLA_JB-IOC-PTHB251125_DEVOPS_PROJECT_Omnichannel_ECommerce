package com.quickbite.edu.dto.request;

import com.quickbite.edu.entity.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255, message = "Tên sản phẩm không quá 255 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Giá cơ bản không được để trống")
    @PositiveOrZero(message = "Giá cơ bản phải lớn hơn hoặc bằng 0")
    private BigDecimal basePrice;

    private Long categoryId;

    private Long brandId;

    private String images;

    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Valid
    private List<ProductVariantRequest> variants;
}