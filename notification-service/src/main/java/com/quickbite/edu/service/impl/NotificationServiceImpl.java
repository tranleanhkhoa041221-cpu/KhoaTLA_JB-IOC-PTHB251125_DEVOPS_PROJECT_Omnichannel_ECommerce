package com.quickbite.edu.service.impl;

import com.quickbite.edu.dto.client.UserContactResponse;
import com.quickbite.edu.dto.request.SendTestNotificationRequest;
import com.quickbite.edu.dto.response.NotificationLogResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.entity.NotificationLog;
import com.quickbite.edu.entity.enums.NotificationChannel;
import com.quickbite.edu.entity.enums.NotificationStatus;
import com.quickbite.edu.entity.enums.NotificationType;
import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import com.quickbite.edu.event.PaymentRefundedEvent;
import com.quickbite.edu.event.PaymentResultEvent;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.mapper.NotificationMapper;
import com.quickbite.edu.repository.NotificationLogRepository;
import com.quickbite.edu.repository.NotificationLogSpecifications;
import com.quickbite.edu.service.interfaces.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trung tam dieu phoi (Choreography Saga - Notification Service la "terminal consumer",
 * KHONG publish lai event nao ca, chi lang nghe va gui thong bao). Voi moi event nhan duoc,
 * gui song song 3 kenh (Email/SMS/Push) - loi o 1 kenh KHONG lam anh huong 2 kenh con lai
 * (moi kenh duoc dispatch() boc try/catch rieng biet va ghi 1 dong NotificationLog rieng).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationMapper notificationMapper;
    private final UserContactService userContactService;
    private final NotificationTemplateService templateService;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final PushNotificationSender pushNotificationSender;

    // LUU Y: KHONG dat @Transactional o cac method handle*() ben duoi - chung goi mang
    // (SMTP that qua LoggingEmailSender, gRPC toi Identity Service) truoc khi ghi DB.
    // Neu bao @Transactional o day, connection DB se bi giu mo suot thoi gian cho SMTP/REST
    // phan hoi (co the vai giay), de gay can kiet connection pool khi tai cao. Tung thao tac
    // doc/ghi trong dispatch() (findByIdempotencyKey/save) van duoc Spring Data JPA tu boc
    // transaction rieng le nhu binh thuong, khong can khai bao them.
    @Override
    public void handleOrderCreated(OrderCreatedEvent event, String kafkaTopic) {
        UserContactResponse contact = userContactService.resolveContact(event.getCustomerId());
        String customerName = contact.getFullName() != null ? contact.getFullName() : contact.getUsername();

        // Tinh san subject/body 1 LAN duy nhat, dung lai ca cho log lan gui va cho hanh dong
        // gui that - tranh goi templateService 2 lan cho cung 1 noi dung trong cung 1 dispatch.
        String emailSubject = templateService.orderCreatedEmailSubject(event);
        String emailBody = templateService.orderCreatedEmailBody(event, customerName);
        String smsBody = templateService.orderCreatedSms(event);
        String pushTitle = templateService.orderCreatedPushTitle();
        String pushBody = templateService.orderCreatedPushBody(event);

        dispatch(event.getCustomerId(), event.getOrderId(), event.getOrderCode(),
                NotificationType.ORDER_CREATED, kafkaTopic, OrderCreatedEvent.class.getSimpleName(),
                NotificationChannel.EMAIL, contact.getEmail(), emailSubject, emailBody,
                () -> emailSender.send(contact.getEmail(), emailSubject, emailBody));

        dispatch(event.getCustomerId(), event.getOrderId(), event.getOrderCode(),
                NotificationType.ORDER_CREATED, kafkaTopic, OrderCreatedEvent.class.getSimpleName(),
                NotificationChannel.SMS, contact.getPhone(), null, smsBody,
                () -> smsSender.send(contact.getPhone(), smsBody));

        dispatch(event.getCustomerId(), event.getOrderId(), event.getOrderCode(),
                NotificationType.ORDER_CREATED, kafkaTopic, OrderCreatedEvent.class.getSimpleName(),
                NotificationChannel.PUSH, "customer:" + event.getCustomerId(), pushTitle, pushBody,
                () -> pushNotificationSender.send(event.getCustomerId(), pushTitle, pushBody));
    }

    @Override
    public void handleOrderCancelled(OrderCancelledEvent event, String kafkaTopic) {
        UserContactResponse contact = userContactService.resolveContact(event.getCustomerId());
        String customerName = contact.getFullName() != null ? contact.getFullName() : contact.getUsername();

        String emailSubject = templateService.orderCancelledEmailSubject(event);
        String emailBody = templateService.orderCancelledEmailBody(event, customerName);
        String smsBody = templateService.orderCancelledSms(event);
        String pushTitle = templateService.orderCancelledPushTitle();
        String pushBody = templateService.orderCancelledPushBody(event);

        dispatch(event.getCustomerId(), event.getOrderId(), event.getOrderCode(),
                NotificationType.ORDER_CANCELLED, kafkaTopic, OrderCancelledEvent.class.getSimpleName(),
                NotificationChannel.EMAIL, contact.getEmail(), emailSubject, emailBody,
                () -> emailSender.send(contact.getEmail(), emailSubject, emailBody));

        dispatch(event.getCustomerId(), event.getOrderId(), event.getOrderCode(),
                NotificationType.ORDER_CANCELLED, kafkaTopic, OrderCancelledEvent.class.getSimpleName(),
                NotificationChannel.SMS, contact.getPhone(), null, smsBody,
                () -> smsSender.send(contact.getPhone(), smsBody));

        dispatch(event.getCustomerId(), event.getOrderId(), event.getOrderCode(),
                NotificationType.ORDER_CANCELLED, kafkaTopic, OrderCancelledEvent.class.getSimpleName(),
                NotificationChannel.PUSH, "customer:" + event.getCustomerId(), pushTitle, pushBody,
                () -> pushNotificationSender.send(event.getCustomerId(), pushTitle, pushBody));
    }

    @Override
    public void handlePaymentCompleted(PaymentResultEvent event, String kafkaTopic) {
        dispatchPaymentEvent(event, kafkaTopic, NotificationType.PAYMENT_COMPLETED);
    }

    @Override
    public void handlePaymentFailed(PaymentResultEvent event, String kafkaTopic) {
        dispatchPaymentEvent(event, kafkaTopic, NotificationType.PAYMENT_FAILED);
    }

    @Override
    public void handlePaymentRefunded(PaymentRefundedEvent event, String kafkaTopic) {
        Long customerId = event.getCustomerId();
        UserContactResponse contact;
        if (customerId != null && customerId > 0) {
            contact = userContactService.resolveContact(customerId);
        } else {
            log.warn("PaymentRefundedEvent thieu customerId cho orderId={}, dung contact fallback", event.getOrderId());
            customerId = 0L;
            contact = UserContactResponse.builder()
                    .email("orders+" + event.getOrderCode() + "@quickbite.local")
                    .phone("0900000000")
                    .fullName("Khach hang")
                    .build();
        }
        final Long cid = customerId;
        final String subject = templateService.paymentRefundedEmailSubject(event);
        final String emailBody = templateService.paymentRefundedEmailBody(event);
        final String smsBody = templateService.paymentRefundedSms(event);
        final String pushTitle = templateService.paymentRefundedPushTitle();
        final String pushBody = templateService.paymentRefundedPushBody(event);
        // Moi lan hoan (ke ca hoan 1 phan nhieu lan) co tong da hoan KHAC nhau -> dung lam khoa idempotency,
        // de event trung (Kafka at-least-once) khong gui 2 lan nhung cac lan hoan khac nhau van duoc bao.
        String disc = event.getTotalRefundedAmount() != null ? event.getTotalRefundedAmount().toPlainString() : "0";
        String type = PaymentRefundedEvent.class.getSimpleName();

        dispatch(cid, event.getOrderId(), event.getOrderCode(), NotificationType.PAYMENT_REFUNDED, kafkaTopic, type,
                NotificationChannel.EMAIL, contact.getEmail(), subject, emailBody,
                () -> emailSender.send(contact.getEmail(), subject, emailBody), disc);
        dispatch(cid, event.getOrderId(), event.getOrderCode(), NotificationType.PAYMENT_REFUNDED, kafkaTopic, type,
                NotificationChannel.SMS, contact.getPhone(), null, smsBody,
                () -> smsSender.send(contact.getPhone(), smsBody), disc);
        dispatch(cid, event.getOrderId(), event.getOrderCode(), NotificationType.PAYMENT_REFUNDED, kafkaTopic, type,
                NotificationChannel.PUSH, "customer:" + cid, pushTitle, pushBody,
                () -> pushNotificationSender.send(cid, pushTitle, pushBody), disc);
    }

    /**
     * PaymentResultEvent mang san customerId. Notification khong can goi nguoc Order Service;
     * chi can goi Identity/User Service qua gRPC de lay contact cua khach hang.
     */
    private void dispatchPaymentEvent(PaymentResultEvent event, String kafkaTopic, NotificationType type) {
        Long customerId = event.getCustomerId();
        UserContactResponse contact;
        if (customerId != null && customerId > 0) {
            contact = userContactService.resolveContact(customerId);
        } else {
            log.warn("PaymentResultEvent thieu customerId cho orderId={}, dung contact fallback", event.getOrderId());
            customerId = 0L;
            contact = UserContactResponse.builder()
                    .email("orders+" + event.getOrderCode() + "@quickbite.local")
                    .phone("0900000000")
                    .fullName("Khach hang")
                    .build();
        }

        String emailSubject;
        String emailBody;
        String smsBody;
        String pushTitle;
        String pushBody;

        if (type == NotificationType.PAYMENT_COMPLETED) {
            emailSubject = templateService.paymentCompletedEmailSubject(event);
            emailBody = templateService.paymentCompletedEmailBody(event);
            smsBody = templateService.paymentCompletedSms(event);
            pushTitle = templateService.paymentCompletedPushTitle();
            pushBody = templateService.paymentCompletedPushBody(event);
        } else {
            emailSubject = templateService.paymentFailedEmailSubject(event);
            emailBody = templateService.paymentFailedEmailBody(event);
            smsBody = templateService.paymentFailedSms(event);
            pushTitle = templateService.paymentFailedPushTitle();
            pushBody = templateService.paymentFailedPushBody(event);
        }

        Long finalCustomerId = customerId;
        dispatch(customerId, event.getOrderId(), event.getOrderCode(), type, kafkaTopic,
                PaymentResultEvent.class.getSimpleName(), NotificationChannel.EMAIL,
                contact.getEmail(), emailSubject, emailBody,
                () -> emailSender.send(contact.getEmail(), emailSubject, emailBody));

        dispatch(customerId, event.getOrderId(), event.getOrderCode(), type, kafkaTopic,
                PaymentResultEvent.class.getSimpleName(), NotificationChannel.SMS,
                contact.getPhone(), null, smsBody,
                () -> smsSender.send(contact.getPhone(), smsBody));

        dispatch(customerId, event.getOrderId(), event.getOrderCode(), type, kafkaTopic,
                PaymentResultEvent.class.getSimpleName(), NotificationChannel.PUSH,
                "customer:" + finalCustomerId, pushTitle, pushBody,
                () -> pushNotificationSender.send(finalCustomerId, pushTitle, pushBody));
    }

    @Override
    public NotificationLogResponse sendTest(SendTestNotificationRequest request) {
        String idempotencyKey = "MANUAL_TEST:" + UUID.randomUUID();
        String effectiveSubject = request.getSubject() != null ? request.getSubject() : "Test notification";

        NotificationLog placeholder = NotificationLog.builder()
                .customerId(0L)
                .orderId(null)
                .orderCode(null)
                .notificationType(NotificationType.MANUAL_TEST)
                .channel(request.getChannel())
                .recipient(request.getRecipient())
                .subject(effectiveSubject)
                .body(request.getBody())
                .status(NotificationStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .kafkaTopic(null)
                .eventType("ManualTest")
                .build();
        NotificationLog saved = notificationLogRepository.saveAndFlush(placeholder);

        NotificationStatus finalStatus = NotificationStatus.SENT;
        String errorMessage = null;
        LocalDateTime sentAt;
        try {
            switch (request.getChannel()) {
                case EMAIL -> emailSender.send(request.getRecipient(), effectiveSubject, request.getBody());
                case SMS -> smsSender.send(request.getRecipient(), request.getBody());
                case PUSH -> pushNotificationSender.send(0L, effectiveSubject, request.getBody());
            }
            sentAt = LocalDateTime.now();
        } catch (Exception ex) {
            finalStatus = NotificationStatus.FAILED;
            errorMessage = ex.getMessage();
            sentAt = null;
            log.error("Gui test notification that bai channel={}", request.getChannel(), ex);
        }

        saved.setStatus(finalStatus);
        saved.setErrorMessage(errorMessage);
        saved.setSentAt(sentAt);
        return notificationMapper.toResponse(notificationLogRepository.save(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationLogResponse getById(Long id) {
        NotificationLog logEntity = notificationLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay notification log id=" + id));
        return notificationMapper.toResponse(logEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationLogResponse> search(
            Long customerId,
            Long orderId,
            NotificationType notificationType,
            NotificationChannel channel,
            NotificationStatus status,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<NotificationLog> spec = Specification
                .where(NotificationLogSpecifications.hasCustomerId(customerId))
                .and(NotificationLogSpecifications.hasOrderId(orderId))
                .and(NotificationLogSpecifications.hasNotificationType(notificationType))
                .and(NotificationLogSpecifications.hasChannel(channel))
                .and(NotificationLogSpecifications.hasStatus(status));

        Page<NotificationLog> result = notificationLogRepository.findAll(spec, pageable);

        return PageResponse.<NotificationLogResponse>builder()
                .content(result.getContent().stream().map(notificationMapper::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    /**
     * Gui 1 kenh thong bao (Email/SMS/Push) roi ghi lai NotificationLog - IDEMPOTENT theo key
     * (notificationType + orderId + channel): neu Kafka redeliver event trung (at-least-once),
     * kenh nay se bi bo qua thay vi gui lai / ghi log trung.
     *
     * QUAN TRONG (fix race condition): INSERT placeholder truoc, GUI sau - khong phai nguoc lai.
     * Neu kiem tra "da ton tai" roi moi gui roi moi luu (thu tu cu), 2 luong xu ly trung event
     * cung luc co the CUNG vuot qua buoc kiem tra (vi ca 2 deu chua thay ban ghi nao), dan den
     * CA HAI cung goi sendAction.run() - tuc gui THAT 2 email/SMS - du sau do DB chi giu duoc
     * 1 dong (unique constraint chan luc save). Insert-first bien unique constraint thanh
     * "khoa" chiem truoc tai DB (atomic o muc transaction/index), nen chi 1 luong duy nhat
     * duoc phep di tiep den buoc goi sendAction that su.
     */
    private void dispatch(
            Long customerId,
            Long orderId,
            String orderCode,
            NotificationType notificationType,
            String kafkaTopic,
            String eventType,
            NotificationChannel channel,
            String recipient,
            String subject,
            String body,
            Runnable sendAction
    ) {
        dispatch(customerId, orderId, orderCode, notificationType, kafkaTopic, eventType, channel,
                recipient, subject, body, sendAction, null);
    }

    private void dispatch(
            Long customerId,
            Long orderId,
            String orderCode,
            NotificationType notificationType,
            String kafkaTopic,
            String eventType,
            NotificationChannel channel,
            String recipient,
            String subject,
            String body,
            Runnable sendAction,
            String keySuffix
    ) {
        String idempotencyKey = notificationType.name() + ":" + orderId + ":" + channel.name()
                + (keySuffix != null ? ":" + keySuffix : "");

        NotificationLog placeholder = NotificationLog.builder()
                .customerId(customerId != null ? customerId : 0L)
                .orderId(orderId)
                .orderCode(orderCode)
                .notificationType(notificationType)
                .channel(channel)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .kafkaTopic(kafkaTopic)
                .eventType(eventType)
                .build();

        NotificationLog savedPlaceholder;
        try {
            savedPlaceholder = notificationLogRepository.saveAndFlush(placeholder);
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint tren idempotency_key chan - da co luong khac dang xu ly/da xu ly
            // xong key nay -> BO QUA HOAN TOAN, khong goi sendAction (tranh gui trung that su).
            log.info("Bo qua notification trung (idempotent, insert-first) key={}", idempotencyKey);
            return;
        }

        NotificationStatus finalStatus = NotificationStatus.SENT;
        String errorMessage = null;
        LocalDateTime sentAt;

        try {
            sendAction.run();
            sentAt = LocalDateTime.now();
        } catch (Exception ex) {
            finalStatus = NotificationStatus.FAILED;
            errorMessage = ex.getMessage();
            sentAt = null;
            log.error("Gui notification that bai key={}", idempotencyKey, ex);
        }

        savedPlaceholder.setStatus(finalStatus);
        savedPlaceholder.setErrorMessage(errorMessage);
        savedPlaceholder.setSentAt(sentAt);
        notificationLogRepository.save(savedPlaceholder);
    }
}
