package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.request.RefundPaymentRequest;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.dto.response.PaymentResponse;
import com.quickbite.edu.dto.response.PaymentUrlResponse;
import com.quickbite.edu.dto.response.TransactionLogResponse;
import com.quickbite.edu.entity.enums.PaymentStatus;
import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

public interface PaymentService {

    /** Goi khi nhan event order.created tu Kafka: khoi tao giao dich PENDING (idempotent). */
    void handleOrderCreated(OrderCreatedEvent event);

    /** Goi khi nhan event order.cancelled tu Kafka: huy giao dich neu con dang PENDING. */
    void handleOrderCancelled(OrderCancelledEvent event);

    /** Sinh URL redirect sang VNPay Sandbox cho 1 don hang. */
    PaymentUrlResponse createPaymentUrl(Long orderId, HttpServletRequest httpRequest);

    PaymentResponse getById(Long id);

    PaymentResponse getByOrderId(Long orderId);

    PageResponse<PaymentResponse> search(Long customerId, PaymentStatus status, int page, int size);

    /** Xu ly webhook IPN tu VNPay (nguon du lieu dang tin cay nhat) - tra ve {RspCode, Message}. */
    Map<String, String> handleIpnCallback(Map<String, String> params);

    /** Xu ly khi khach duoc VNPay redirect ve returnUrl (chi phuc vu hien thi UX cho khach). */
    PaymentResponse handleReturnCallback(Map<String, String> params);

    /** Hoan tien mot phan/toan bo 1 giao dich da SUCCESS. */
    PaymentResponse refund(Long id, RefundPaymentRequest request, HttpServletRequest httpRequest);

    /** Doi soat: chu dong hoi VNPay trang thai that su cua giao dich (que.ry.dr). */
    PaymentResponse queryTransaction(Long id, HttpServletRequest httpRequest);

    List<TransactionLogResponse> getLogs(Long paymentId);
}
