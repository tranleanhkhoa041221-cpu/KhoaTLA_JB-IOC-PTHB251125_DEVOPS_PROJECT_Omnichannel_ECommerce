package com.quickbite.edu.service.impl;

import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import com.quickbite.edu.event.OrderItemEvent;
import com.quickbite.edu.event.PaymentRefundedEvent;
import com.quickbite.edu.event.PaymentResultEvent;
import com.quickbite.edu.service.interfaces.NotificationTemplateService;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    @Override
    public String orderCreatedEmailSubject(OrderCreatedEvent event) {
        return "Xac nhan don hang " + event.getOrderCode();
    }

    @Override
    public String orderCreatedEmailBody(OrderCreatedEvent event, String customerName) {
        String items = event.getItems() == null ? "" : event.getItems().stream()
                .map(this::formatItem)
                .collect(Collectors.joining("\n"));
        return """
                Xin chao %s,

                Cam on ban da dat hang tai QuickBite.
                Ma don: %s
                Tong tien: %s VND
                Dia chi giao: %s

                Chi tiet:
                %s

                Vui long hoan tat thanh toan de xac nhan don hang.
                """.formatted(
                customerName,
                event.getOrderCode(),
                event.getTotalAmount(),
                event.getShippingAddress(),
                items
        );
    }

    @Override
    public String orderCreatedSms(OrderCreatedEvent event) {
        return "QuickBite: Don " + event.getOrderCode() + " da tao. Tong " + event.getTotalAmount() + " VND. Vui long thanh toan.";
    }

    @Override
    public String orderCreatedPushTitle() {
        return "Dat hang thanh cong";
    }

    @Override
    public String orderCreatedPushBody(OrderCreatedEvent event) {
        return "Don " + event.getOrderCode() + " dang cho thanh toan.";
    }

    @Override
    public String orderCancelledEmailSubject(OrderCancelledEvent event) {
        return "Don hang " + event.getOrderCode() + " da bi huy";
    }

    @Override
    public String orderCancelledEmailBody(OrderCancelledEvent event, String customerName) {
        return """
                Xin chao %s,

                Don hang %s da duoc huy.
                Ly do: %s

                Neu can ho tro, vui long lien he hotline QuickBite.
                """.formatted(customerName, event.getOrderCode(), event.getReason());
    }

    @Override
    public String orderCancelledSms(OrderCancelledEvent event) {
        return "QuickBite: Don " + event.getOrderCode() + " da huy. Ly do: " + event.getReason();
    }

    @Override
    public String orderCancelledPushTitle() {
        return "Don hang da huy";
    }

    @Override
    public String orderCancelledPushBody(OrderCancelledEvent event) {
        return "Don " + event.getOrderCode() + " khong con hieu luc.";
    }

    @Override
    public String paymentCompletedEmailSubject(PaymentResultEvent event) {
        return "Thanh toan thanh cong - " + event.getOrderCode();
    }

    @Override
    public String paymentCompletedEmailBody(PaymentResultEvent event) {
        return """
                Thanh toan cho don %s da thanh cong.
                Ma giao dich: %s
                Don hang dang duoc xu ly va se duoc giao som.
                """.formatted(event.getOrderCode(), event.getTransactionId());
    }

    @Override
    public String paymentCompletedSms(PaymentResultEvent event) {
        return "QuickBite: TT thanh cong don " + event.getOrderCode() + ". GD: " + event.getTransactionId();
    }

    @Override
    public String paymentCompletedPushTitle() {
        return "Thanh toan thanh cong";
    }

    @Override
    public String paymentCompletedPushBody(PaymentResultEvent event) {
        return "Don " + event.getOrderCode() + " da thanh toan xong, dang duoc xu ly.";
    }

    @Override
    public String paymentFailedEmailSubject(PaymentResultEvent event) {
        return "Thanh toan that bai - " + event.getOrderCode();
    }

    @Override
    public String paymentFailedEmailBody(PaymentResultEvent event) {
        return """
                Thanh toan cho don %s khong thanh cong.
                Ly do: %s
                Don hang da duoc huy va ton kho da duoc hoan tra.
                """.formatted(event.getOrderCode(), event.getReason());
    }

    @Override
    public String paymentFailedSms(PaymentResultEvent event) {
        return "QuickBite: TT that bai don " + event.getOrderCode() + ". " + event.getReason();
    }

    @Override
    public String paymentFailedPushTitle() {
        return "Thanh toan that bai";
    }

    @Override
    public String paymentFailedPushBody(PaymentResultEvent event) {
        return "Don " + event.getOrderCode() + " thanh toan khong thanh cong, don da bi huy.";
    }

    @Override
    public String paymentRefundedEmailSubject(PaymentRefundedEvent event) {
        return "Hoan tien don hang " + event.getOrderCode();
    }

    @Override
    public String paymentRefundedEmailBody(PaymentRefundedEvent event) {
        return """
                Don %s da duoc hoan tien.
                So tien hoan lan nay: %s VND
                Tong da hoan: %s / %s VND (%s)
                Ly do: %s
                Tien se ve tai khoan/the cua ban theo thoi gian xu ly cua ngan hang.
                """.formatted(event.getOrderCode(), event.getRefundAmount(), event.getTotalRefundedAmount(),
                event.getPaymentAmount(), event.isFullyRefunded() ? "hoan toan bo" : "hoan mot phan",
                event.getReason());
    }

    @Override
    public String paymentRefundedSms(PaymentRefundedEvent event) {
        return "QuickBite: don " + event.getOrderCode() + " da hoan " + event.getRefundAmount() + " VND.";
    }

    @Override
    public String paymentRefundedPushTitle() {
        return "Hoan tien thanh cong";
    }

    @Override
    public String paymentRefundedPushBody(PaymentRefundedEvent event) {
        return "Don " + event.getOrderCode() + " da duoc hoan " + event.getRefundAmount() + " VND.";
    }

    private String formatItem(OrderItemEvent item) {
        return "- " + item.getProductName() + " x" + item.getQuantity() + " (" + item.getSku() + ")";
    }
}
