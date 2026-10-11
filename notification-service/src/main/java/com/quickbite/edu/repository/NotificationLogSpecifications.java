package com.quickbite.edu.repository;

import com.quickbite.edu.entity.NotificationLog;
import com.quickbite.edu.entity.enums.NotificationChannel;
import com.quickbite.edu.entity.enums.NotificationStatus;
import com.quickbite.edu.entity.enums.NotificationType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Moi method tra ve 1 dieu kien don le. Khi tham so KHONG duoc truyen, tra ve dieu kien "khong loc"
 * (cb.conjunction() = luon dung) - TUYET DOI KHONG tra ve null: Spring Data JPA moi (Spring Boot 4)
 * nem IllegalArgumentException "Specification must not be null" khi Specification.where(null)/and(null),
 * lam API GET /api/notifications/logs tra loi 500/400 moi khi thieu bat ky filter nao.
 * Ghep lai bang Specification.where(...).and(...) trong NotificationServiceImpl.search().
 */
public final class NotificationLogSpecifications {

    private NotificationLogSpecifications() {
    }

    private static Specification<NotificationLog> unrestricted() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<NotificationLog> hasCustomerId(Long customerId) {
        return customerId == null ? unrestricted()
                : (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<NotificationLog> hasOrderId(Long orderId) {
        return orderId == null ? unrestricted()
                : (root, query, cb) -> cb.equal(root.get("orderId"), orderId);
    }

    public static Specification<NotificationLog> hasNotificationType(NotificationType type) {
        return type == null ? unrestricted()
                : (root, query, cb) -> cb.equal(root.get("notificationType"), type);
    }

    public static Specification<NotificationLog> hasChannel(NotificationChannel channel) {
        return channel == null ? unrestricted()
                : (root, query, cb) -> cb.equal(root.get("channel"), channel);
    }

    public static Specification<NotificationLog> hasStatus(NotificationStatus status) {
        return status == null ? unrestricted()
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
