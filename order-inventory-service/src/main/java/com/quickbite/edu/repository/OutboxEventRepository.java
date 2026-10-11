package com.quickbite.edu.repository;

import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Tang tu Top50 -> Top200 (Performance/Scalability): voi fixedDelay=1000ms, gioi han cu
    // (50) chi cho phep toi da 50 event/giay duoc "lay ra de publish" - THAP HON RAT NHIEU so
    // voi muc tieu demo Flash Sale 500-1000 req/s cua de bai, khien outbox_events bi don (backlog)
    // tang khong gioi han trong luc tai cao du OutboxDispatchOps van xu ly dung (khong mat/trung
    // event). Ket hop voi viec publish SONG SONG nhieu event/tick (xem OutboxEventPublisher).
    List<OutboxEvent> findTop200ByStatusOrderByIdAsc(OutboxStatus status);

    /**
     * SELECT ... FOR UPDATE tren DUNG 1 ban ghi - dung de "claim" (gianh quyen xu ly) 1 outbox
     * event truoc khi publish len Kafka. Khi chay nhieu instance cua service nay (horizontal
     * scaling - dung muc dich cua kien truc Microservices theo de bai), moi instance co 1
     * @Scheduled job rieng cung doc findTop200ByStatusOrderByIdAsc() gan nhu dong thoi; neu
     * khong co lock nay, CA 2 instance se cung publish TRUNG 1 event len Kafka. Voi lock nay:
     * instance thu 2 se cho (block) tai dong findByIdForUpdate() cho den khi instance thu 1
     * COMMIT xong (status da thanh PUBLISHED), luc do no doc lai thay status != PENDING va
     * tu bo qua - khong publish trung.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEvent o where o.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);
}
