package com.quickbite.edu.service.impl;

import com.quickbite.edu.dto.request.RefundPaymentRequest;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.dto.response.PaymentResponse;
import com.quickbite.edu.dto.response.PaymentUrlResponse;
import com.quickbite.edu.dto.response.TransactionLogResponse;
import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.Payment;
import com.quickbite.edu.entity.PaymentTransactionLog;
import com.quickbite.edu.entity.enums.OutboxStatus;
import com.quickbite.edu.entity.enums.PaymentMethod;
import com.quickbite.edu.entity.enums.PaymentStatus;
import com.quickbite.edu.entity.enums.TransactionLogType;
import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import com.quickbite.edu.event.PaymentRefundedEvent;
import com.quickbite.edu.event.PaymentResultEvent;
import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.ForbiddenException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.exception.PaymentGatewayException;
import com.quickbite.edu.kafka.KafkaTopics;
import com.quickbite.edu.mapper.PaymentMapper;
import com.quickbite.edu.repository.PaymentRepository;
import com.quickbite.edu.repository.PaymentTransactionLogRepository;
import com.quickbite.edu.service.interfaces.PaymentService;
import com.quickbite.edu.vnpay.VnPayProperties;
import com.quickbite.edu.vnpay.VnPayService;
import com.quickbite.edu.vnpay.VnPayUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionLogRepository transactionLogRepository;
    private final PaymentPersistenceOps paymentPersistenceOps;
    private final PaymentMapper paymentMapper;
    private final VnPayService vnPayService;
    private final VnPayProperties vnPayProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (paymentRepository.existsByOrderId(event.getOrderId())) {
            log.warn("Payment cho orderId={} da ton tai - bo qua (Kafka co the gui lai event trung)", event.getOrderId());
            return;
        }

        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .orderCode(event.getOrderCode())
                .customerId(event.getCustomerId())
                .amount(event.getTotalAmount())
                .currency("VND")
                .paymentMethod(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .vnpTxnRef(generateVnpTxnRef())
                .refundedAmount(BigDecimal.ZERO)
                .build();

        paymentRepository.save(payment);
        log.info("Da khoi tao giao dich PENDING cho orderId={}, orderCode={}, amount={}",
                event.getOrderId(), event.getOrderCode(), event.getTotalAmount());
    }

    @Override
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        Optional<Payment> optPayment = paymentRepository.findByOrderId(event.getOrderId());
        if (optPayment.isEmpty()) {
            return;
        }
        Payment payment = optPayment.get();
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("Bo qua handleOrderCancelled cho orderId={} vi Payment dang o trang thai {}",
                    event.getOrderId(), payment.getStatus());
            return;
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setFailureReason("Don hang da bi huy: " + event.getReason());
        paymentRepository.save(payment);
        log.info("Da huy giao dich thanh toan cua orderId={} do don hang bi huy", event.getOrderId());
    }

    @Override
    @Transactional
    public PaymentUrlResponse createPaymentUrl(Long orderId, HttpServletRequest httpRequest) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Chua co giao dich thanh toan nao cho orderId=" + orderId));

        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new BadRequestException("Giao dich khong o trang thai co the thanh toan (hien tai: " + payment.getStatus() + ")");
        }

        if (payment.getVnpCreateDate() == null) {
            payment.setVnpCreateDate(VnPayUtils.now());
        }
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        String clientIp = VnPayUtils.getClientIpAddress(httpRequest);
        String paymentUrl = vnPayService.buildPaymentUrl(payment, clientIp);

        saveLog(payment.getId(), TransactionLogType.CREATE_PAYMENT_URL, "clientIp=" + clientIp,
                paymentUrl, null, "Da sinh URL thanh toan VNPay Sandbox");

        LocalDateTime expireAt = VnPayUtils.nowVn().plusMinutes(vnPayProperties.getPaymentExpireMinutes());
        return PaymentUrlResponse.builder()
                .paymentUrl(paymentUrl)
                .vnpTxnRef(payment.getVnpTxnRef())
                .expireAt(expireAt)
                .build();
    }

    @Override
    public PaymentResponse getById(Long id) {
        return paymentMapper.toResponse(findPaymentOrThrow(id));
    }

    @Override
    public PaymentResponse getByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Chua co giao dich thanh toan nao cho orderId=" + orderId));
        return paymentMapper.toResponse(payment);
    }

    @Override
    public PageResponse<PaymentResponse> search(Long customerId, PaymentStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> result;
        if (customerId != null && status != null) {
            result = paymentRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        } else if (customerId != null) {
            result = paymentRepository.findByCustomerId(customerId, pageable);
        } else if (status != null) {
            result = paymentRepository.findByStatus(status, pageable);
        } else {
            result = paymentRepository.findAll(pageable);
        }

        return PageResponse.<PaymentResponse>builder()
                .content(result.getContent().stream().map(paymentMapper::toResponse).toList())
                .pageNumber(result.getNumber())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .isLast(result.isLast())
                .build();
    }

    @Override
    public Map<String, String> handleIpnCallback(Map<String, String> params) {
        Map<String, String> response = new LinkedHashMap<>();
        try {
            GatewayResult result = processGatewayResult(params, TransactionLogType.IPN_CALLBACK);
            response.put("RspCode", result.rspCode());
            response.put("Message", result.message());
        } catch (Exception ex) {
            log.error("Loi khong xac dinh khi xu ly IPN tu VNPay, params={}", params, ex);
            response.put("RspCode", "99");
            response.put("Message", "Unknown error");
        }
        return response;
    }

    @Override
    public PaymentResponse handleReturnCallback(Map<String, String> params) {
        // Return URL chi phuc vu hien thi UX cho khach (browser-controlled, de bi gia mao hon IPN)
        // nen van xu ly cap nhat trang thai (thuan tien test local qua Postman/trinh duyet khi
        // chua co public URL cho IPN) nhung KHONG duoc coi la nguon xac nhan thanh toan chinh thuc.
        //
        // BAO MAT (IDOR / Data Leakage - sua ngay 2026-10): day la endpoint PUBLIC, KHONG co
        // Authentication (VNPay redirect thang trinh duyet khach, khong the dinh kem Bearer token).
        // TRUOC DAY method nay goi processGatewayResult() de GHI LOG/cap nhat trang thai nhung
        // BO QUA ket qua tra ve (khong kiem tra rspCode) roi VAN CU tra ve toan bo PaymentResponse
        // (customerId, amount, bankCode, cardType...) chi can biet/doan duoc 1 vnp_TxnRef hop le -
        // ke ca khi vnp_SecureHash SAI/THIEU (rspCode=97, chu ky khong hop le). Ke tan cong KHONG
        // can dang nhap, chi can goi GET .../vnpay/return?vnp_TxnRef=<doan hoac ro ri tu noi khac>
        // la xem duoc thong tin giao dich CUA NGUOI KHAC - dung ngu nghia IDOR/data leakage qua
        // 1 API Get cong khai. Sua: BAT BUOC vnp_SecureHash phai hop le (rspCode != "97") truoc khi
        // duoc phep doc du lieu Payment - vi chu ky HMAC-SHA512 chi VNPay (hoac server minh, giu
        // secret key) tao duoc, nen "co chu ky hop le" tuong duong "day la redirect THAT cua VNPay
        // cho DUNG khach hang vua thanh toan giao dich nay", khong ai khac gia mao duoc.
        GatewayResult result = processGatewayResult(params, TransactionLogType.RETURN_URL);
        if ("97".equals(result.rspCode())) {
            throw new ForbiddenException("Chu ky (vnp_SecureHash) khong hop le, tu choi truy cap thong tin giao dich");
        }

        String txnRef = params.get("vnp_TxnRef");
        Payment payment = paymentRepository.findByVnpTxnRef(txnRef)
                .orElseThrow(() -> new NotFoundException("Khong tim thay giao dich voi vnp_TxnRef=" + txnRef));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refund(Long id, RefundPaymentRequest request, HttpServletRequest httpRequest) {
        Payment payment = findPaymentOrThrow(id);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException("Chi co the hoan tien giao dich da thanh toan thanh cong (SUCCESS)");
        }

        BigDecimal remaining = payment.getAmount().subtract(payment.getRefundedAmount());
        if (request.getAmount().compareTo(remaining) > 0) {
            throw new BadRequestException("So tien hoan (" + request.getAmount()
                    + ") vuot qua so tien con lai co the hoan (" + remaining + ")");
        }

        String clientIp = VnPayUtils.getClientIpAddress(httpRequest);
        Map<String, String> vnpResponse = vnPayService.refund(payment, request.getAmount(), request.getReason(),
                request.getCreateBy(), clientIp);
        String responseCode = vnpResponse.get("vnp_ResponseCode");

        saveLog(payment.getId(), TransactionLogType.REFUND,
                "amount=" + request.getAmount() + ";reason=" + request.getReason(),
                String.valueOf(vnpResponse), responseCode, vnpResponse.get("vnp_Message"));

        if (!"00".equals(responseCode)) {
            throw new PaymentGatewayException("VNPay tu choi yeu cau hoan tien, ma loi: " + responseCode);
        }

        payment.setRefundedAmount(payment.getRefundedAmount().add(request.getAmount()));
        boolean fullyRefunded = payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0;
        if (fullyRefunded) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }

        // Choreography Saga: ghi Payment + OutboxEvent "payment.refunded" trong CUNG 1 transaction.
        // Order Service nghe de doi trang thai don (PARTIALLY_REFUNDED/REFUNDED), Notification Service
        // nghe de bao khach. KHONG tu cong lai kho o day (hoan tien != hang da ve kho).
        PaymentRefundedEvent refundedEvent = PaymentRefundedEvent.builder()
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .orderCode(payment.getOrderCode())
                .transactionId(payment.getVnpTransactionNo())
                .refundAmount(request.getAmount())
                .totalRefundedAmount(payment.getRefundedAmount())
                .paymentAmount(payment.getAmount())
                .fullyRefunded(fullyRefunded)
                .reason(request.getReason())
                .build();
        OutboxEvent refundOutbox = OutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId(String.valueOf(payment.getId()))
                .eventType(PaymentRefundedEvent.class.getSimpleName())
                .topic(KafkaTopics.PAYMENT_REFUNDED)
                .payload(objectMapper.writeValueAsString(refundedEvent))
                .status(OutboxStatus.PENDING)
                .build();
        paymentPersistenceOps.updateStatusWithOutbox(payment, refundOutbox);
        log.info("Da hoan tien {} cho paymentId={} (orderCode={})", request.getAmount(), id, payment.getOrderCode());
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse queryTransaction(Long id, HttpServletRequest httpRequest) {
        Payment payment = findPaymentOrThrow(id);
        String clientIp = VnPayUtils.getClientIpAddress(httpRequest);
        Map<String, String> vnpResponse = vnPayService.queryTransaction(payment, clientIp);

        saveLog(payment.getId(), TransactionLogType.QUERY_TRANSACTION, null,
                String.valueOf(vnpResponse), vnpResponse.get("vnp_ResponseCode"), "Doi soat voi VNPay (querydr)");

        String transactionStatus = vnpResponse.get("vnp_TransactionStatus");
        if ("00".equals(transactionStatus) && payment.getStatus() != PaymentStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setVnpTransactionNo(vnpResponse.get("vnp_TransactionNo"));
            paymentRepository.save(payment);
            log.info("Doi soat: dong bo lai trang thai SUCCESS cho paymentId={} theo du lieu tu VNPay", id);
        }
        return paymentMapper.toResponse(payment);
    }

    @Override
    public List<TransactionLogResponse> getLogs(Long paymentId) {
        return transactionLogRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId).stream()
                .map(paymentMapper::toLogResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Logic dung chung xu ly ket qua tra ve tu VNPay (return URL & IPN)
    // ------------------------------------------------------------------

    // Luu y: KHONG danh dau @Transactional o day - day la method noi bo, tu goi qua
    // "this." nen proxy AOP cua Spring se khong intercept duoc (self-invocation).
    // Doan ghi Payment+OutboxEvent thuc su can atomic da duoc tach rieng vao
    // paymentPersistenceOps.updateStatusWithOutbox(...) (goi qua Spring bean that).
    private GatewayResult processGatewayResult(Map<String, String> params, TransactionLogType logType) {
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return new GatewayResult("01", "Order not found (thieu vnp_TxnRef)");
        }

        if (!vnPayService.verifySignature(params)) {
            saveLog(null, logType, String.valueOf(params), null, "97", "Sai chu ky (invalid signature)");
            return new GatewayResult("97", "Invalid signature");
        }

        Optional<Payment> optPayment = paymentRepository.findByVnpTxnRef(txnRef);
        if (optPayment.isEmpty()) {
            saveLog(null, logType, String.valueOf(params), null, "01", "Khong tim thay giao dich TxnRef=" + txnRef);
            return new GatewayResult("01", "Order not found");
        }
        Payment payment = optPayment.get();

        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            saveLog(payment.getId(), logType, String.valueOf(params), null, "02",
                    "Giao dich da duoc xu ly truoc do (trang thai hien tai: " + payment.getStatus() + ")");
            return new GatewayResult("02", "Order already confirmed");
        }

        String vnpAmountStr = params.get("vnp_Amount");
        BigDecimal receivedAmount = (vnpAmountStr == null || vnpAmountStr.isBlank())
                ? null : new BigDecimal(vnpAmountStr).divide(BigDecimal.valueOf(100));
        if (receivedAmount == null || receivedAmount.compareTo(payment.getAmount()) != 0) {
            saveLog(payment.getId(), logType, String.valueOf(params), null, "04", "So tien khong khop voi giao dich goc");
            return new GatewayResult("04", "Invalid amount");
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);

        OutboxEvent outboxEvent;
        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
            payment.setBankCode(params.get("vnp_BankCode"));
            payment.setCardType(params.get("vnp_CardType"));
            String payDate = params.get("vnp_PayDate");
            if (payDate != null && !payDate.isBlank()) {
                payment.setPaidAt(VnPayUtils.parse(payDate));
            }
            PaymentResultEvent event = PaymentResultEvent.builder()
                    .orderId(payment.getOrderId())
                    .customerId(payment.getCustomerId())
                    .orderCode(payment.getOrderCode())
                    .transactionId(payment.getVnpTransactionNo())
                    .build();
            outboxEvent = buildOutboxEvent(payment, KafkaTopics.PAYMENT_COMPLETED, event);
        } else {
            String reason = "VNPay tra ve ma loi " + responseCode;
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(reason);
            PaymentResultEvent event = PaymentResultEvent.builder()
                    .orderId(payment.getOrderId())
                    .customerId(payment.getCustomerId())
                    .orderCode(payment.getOrderCode())
                    .reason(reason)
                    .build();
            outboxEvent = buildOutboxEvent(payment, KafkaTopics.PAYMENT_FAILED, event);
        }

        paymentPersistenceOps.updateStatusWithOutbox(payment, outboxEvent);
        saveLog(payment.getId(), logType, String.valueOf(params), null, responseCode,
                "Da cap nhat trang thai: " + payment.getStatus());

        return new GatewayResult("00", "Confirm Success");
    }

    private OutboxEvent buildOutboxEvent(Payment payment, String topic, PaymentResultEvent event) {
        return OutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId(String.valueOf(payment.getId()))
                .eventType(PaymentResultEvent.class.getSimpleName())
                .topic(topic)
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .build();
    }

    private void saveLog(Long paymentId, TransactionLogType type, String requestParams, String responseParams,
                          String responseCode, String message) {
        PaymentTransactionLog logEntry = PaymentTransactionLog.builder()
                .paymentId(paymentId)
                .type(type)
                .requestParams(requestParams)
                .responseParams(responseParams)
                .responseCode(responseCode)
                .message(message)
                .build();
        transactionLogRepository.save(logEntry);
    }

    private Payment findPaymentOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay giao dich thanh toan co id=" + id));
    }

    private String generateVnpTxnRef() {
        return "PAY" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }

    private record GatewayResult(String rspCode, String message) {
    }
}
