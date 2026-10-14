package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import com.quickbite.edu.event.PaymentRefundedEvent;
import com.quickbite.edu.event.PaymentResultEvent;

public interface NotificationTemplateService {

    String orderCreatedEmailSubject(OrderCreatedEvent event);

    String orderCreatedEmailBody(OrderCreatedEvent event, String customerName);

    String orderCreatedSms(OrderCreatedEvent event);

    String orderCreatedPushTitle();

    String orderCreatedPushBody(OrderCreatedEvent event);

    String orderCancelledEmailSubject(OrderCancelledEvent event);

    String orderCancelledEmailBody(OrderCancelledEvent event, String customerName);

    String orderCancelledSms(OrderCancelledEvent event);

    String orderCancelledPushTitle();

    String orderCancelledPushBody(OrderCancelledEvent event);

    String paymentCompletedEmailSubject(PaymentResultEvent event);

    String paymentCompletedEmailBody(PaymentResultEvent event);

    String paymentCompletedSms(PaymentResultEvent event);

    String paymentCompletedPushTitle();

    String paymentCompletedPushBody(PaymentResultEvent event);

    String paymentFailedEmailSubject(PaymentResultEvent event);

    String paymentFailedEmailBody(PaymentResultEvent event);

    String paymentFailedSms(PaymentResultEvent event);

    String paymentFailedPushTitle();

    String paymentFailedPushBody(PaymentResultEvent event);

    String paymentRefundedEmailSubject(PaymentRefundedEvent event);

    String paymentRefundedEmailBody(PaymentRefundedEvent event);

    String paymentRefundedSms(PaymentRefundedEvent event);

    String paymentRefundedPushTitle();

    String paymentRefundedPushBody(PaymentRefundedEvent event);
}
