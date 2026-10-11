package com.quickbite.edu.event;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Event LANG NGHE tu topic "order.created" duoc Order & Inventory Service
 * phat ra (qua Outbox Pattern) ngay sau khi don hang duoc tao va ton kho da
 * duoc giu (reserve) thanh cong.
 * CAU TRUC PHAI KHOP CHINH XAC voi order-inventory-service.event.OrderCreatedEvent
 * vi day la "hop dong" (contract) giao tiep bat dong bo giua 2 service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent implements Serializable {
    private Long orderId;
    private String orderCode;
    private Long customerId;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private List<OrderItemEvent> items;
    private LocalDateTime createdAt;
}
