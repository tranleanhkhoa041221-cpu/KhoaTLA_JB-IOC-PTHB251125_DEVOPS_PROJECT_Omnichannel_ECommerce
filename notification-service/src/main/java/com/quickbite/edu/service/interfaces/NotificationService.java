package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.request.SendTestNotificationRequest;
import com.quickbite.edu.dto.response.NotificationLogResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.entity.enums.NotificationChannel;
import com.quickbite.edu.entity.enums.NotificationStatus;
import com.quickbite.edu.entity.enums.NotificationType;
import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import com.quickbite.edu.event.PaymentRefundedEvent;
import com.quickbite.edu.event.PaymentResultEvent;

public interface NotificationService {

    void handleOrderCreated(OrderCreatedEvent event, String kafkaTopic);

    void handleOrderCancelled(OrderCancelledEvent event, String kafkaTopic);

    void handlePaymentCompleted(PaymentResultEvent event, String kafkaTopic);

    void handlePaymentFailed(PaymentResultEvent event, String kafkaTopic);

    void handlePaymentRefunded(PaymentRefundedEvent event, String kafkaTopic);

    /** Gui thu 1 thong bao THAT (khong qua Kafka) - phuc vu test tren Postman/demo. */
    NotificationLogResponse sendTest(SendTestNotificationRequest request);

    NotificationLogResponse getById(Long id);

    PageResponse<NotificationLogResponse> search(
            Long customerId,
            Long orderId,
            NotificationType notificationType,
            NotificationChannel channel,
            NotificationStatus status,
            int page,
            int size
    );
}
