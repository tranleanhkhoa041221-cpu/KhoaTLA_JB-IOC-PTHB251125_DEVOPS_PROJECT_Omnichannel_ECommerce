package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.request.CancelOrderRequest;
import com.quickbite.edu.dto.request.CreateOrderRequest;
import com.quickbite.edu.dto.response.OrderResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.entity.enums.OrderStatus;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getById(Long id);

    OrderResponse getByOrderCode(String orderCode);

    PageResponse<OrderResponse> search(Long customerId, OrderStatus status, int page, int size);

    OrderResponse cancelOrder(Long id, CancelOrderRequest request);

    /** Goi khi nhan event payment.completed tu Kafka (Choreography Saga). */
    void confirmOrder(Long orderId, String transactionId);

    /** Goi khi nhan event payment.failed tu Kafka - compensating transaction: tra lai kho + huy don. */
    void cancelOrderFromPaymentFailure(Long orderId, String reason);
}
