package com.quickbite.edu.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockInitRequest {

    @NotBlank(message = "SKU khong duoc de trong")
    private String sku;

    @NotBlank(message = "Ten san pham khong duoc de trong")
    private String productName;

    @NotNull(message = "So luong khong duoc de trong")
    @Min(value = 0, message = "So luong khong duoc am")
    private Integer quantity;
}
