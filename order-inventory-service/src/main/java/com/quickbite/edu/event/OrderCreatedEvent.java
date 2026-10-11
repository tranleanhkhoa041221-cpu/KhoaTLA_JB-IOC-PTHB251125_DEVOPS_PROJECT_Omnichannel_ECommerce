package com.quickbite.edu.event;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Event duoc Order Service phat ra (qua topic "order.created") ngay sau khi
 * don hang duoc tao va ton kho da duoc giu (reserve) thanh cong.
 * - Notification Service lang nghe de gui email xac nhan don hang.
 * - Payment Service lang nghe de khoi tao giao dich thanh toan.
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
