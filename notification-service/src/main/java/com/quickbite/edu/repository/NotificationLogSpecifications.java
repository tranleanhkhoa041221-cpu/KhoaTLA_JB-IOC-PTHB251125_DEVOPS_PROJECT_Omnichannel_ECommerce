package com.quickbite.edu.repository;

import com.quickbite.edu.entity.NotificationLog;
import com.quickbite.edu.entity.enums.NotificationChannel;
import com.quickbite.edu.entity.enums.NotificationStatus;
import com.quickbite.edu.entity.enums.NotificationType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Moi method tra ve 1 dieu kien don le (hoac null neu tham so khong duoc truyen) - ghep lai
 * bang Specification.allOf(...) trong NotificationServiceImpl.search(). Cach lam nay thay
 * the cho viec viet nhieu method findByXAndYAndZ rieng le trong Repository ung voi tung to
 * hop filter co the co (5 filter doc lap se can toi 2^5 = 32 to hop neu lam theo kieu cu).
 */
public final class NotificationLogSpecifications {

    private NotificationLogSpecifications() {
    }

    public static Specification<NotificationLog> hasCustomerId(Long customerId) {
        return customerId == null ? null
                : (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<NotificationLog> hasOrderId(Long orderId) {
        return orderId == null ? null
                : (root, query, cb) -> cb.equal(root.get("orderId"), orderId);
    }

    public static Specification<NotificationLog> hasNotificationType(NotificationType type) {
        return type == null ? null
                : (root, query, cb) -> cb.equal(root.get("notificationType"), type);
    }

    public static Specification<NotificationLog> hasChannel(NotificationChannel channel) {
        return channel == null ? null
                : (root, query, cb) -> cb.equal(root.get("channel"), channel);
    }

    public static Specification<NotificationLog> hasStatus(NotificationStatus status) {
        return status == null ? null
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
