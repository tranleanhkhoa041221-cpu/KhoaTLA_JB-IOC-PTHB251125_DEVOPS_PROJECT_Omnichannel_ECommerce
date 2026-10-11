package com.quickbite.edu.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    /**
     * Idempotency Key do client tao (UUID v4).
     */
    @NotBlank(message = "idempotencyKey khong duoc de trong (UUID do client tao)")
    private String idempotencyKey;

    @NotNull(message = "customerId khong duoc de trong")
    private Long customerId;

    private String note;

    @NotEmpty(message = "Don hang phai co it nhat 1 san pham")
    @Valid
    private List<OrderItemRequest> items;
}
