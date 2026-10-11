package com.quickbite.edu.event;

import lombok.*;

import java.math.BigDecimal;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemEvent implements Serializable {
    private String sku;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
}
