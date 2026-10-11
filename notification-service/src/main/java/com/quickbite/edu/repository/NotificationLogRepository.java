package com.quickbite.edu.repository;

import com.quickbite.edu.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * JpaSpecificationExecutor cho phep loc theo BAT KY to hop nao cua cac truong
 * (customerId/orderId/notificationType/channel/status) ma khong can khai bao
 * hang chuc method findByXAndYAndZ... rieng le (tranh combinatorial explosion).
 * Xem NotificationLogSpecifications de biet cach ghep dieu kien.
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long>,
        JpaSpecificationExecutor<NotificationLog> {

    /** Phuc vu idempotency: tranh gui trung thong bao khi Kafka gui lai event (at-least-once). */
    Optional<NotificationLog> findByIdempotencyKey(String idempotencyKey);
}
