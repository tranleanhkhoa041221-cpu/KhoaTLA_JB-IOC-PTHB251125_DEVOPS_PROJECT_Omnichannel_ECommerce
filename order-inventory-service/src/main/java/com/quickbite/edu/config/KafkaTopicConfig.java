package com.quickbite.edu.config;

import com.quickbite.edu.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Khai bao tuong minh so partition cho cac topic do Order & Inventory Service PHAT ra
 * (order.created, order.cancelled).
 *
 * LY DO (Performance/Scalability - Flash Sale 500-1000 req/s): neu KHONG khai bao NewTopic,
 * Kafka se auto-create topic voi "num.partitions" MAC DINH cua broker (thuong chi la 1 partition
 * tren cau hinh dev/docker-compose don gian). Voi 1 partition, moi consumer group (Payment Service,
 * Notification Service, chinh Order Service nghe lai payment.*) CHI co the chay TOI DA 1 consumer
 * thread xu ly topic do - dong thoi ap dung spring.kafka.listener.concurrency > 1 o phia consumer
 * se VO NGHIA (Kafka khong the giao > 1 partition cho > 1 thread trong cung 1 group). Ket qua: khi
 * Flash Sale sinh ra hang tram OrderCreatedEvent/giay, Notification/Payment Service chi xu ly
 * TUAN TU tung message mot -> backlog tang nhanh, tang do tre xu ly thanh toan/gui thong bao
 * (du khong lam sai lech du lieu vi xu ly van dung thu tu, nhung KHONG dap ung duoc throughput
 * muc tieu cua bai demo).
 *
 * replicas = 1 vi moi trong cau hinh dev/demo (1 Kafka broker). Khi len VPS/production that voi
 * >= 3 broker, tang replicationFactor len >= 2 de chiu loi broker.
 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CREATED)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CANCELLED)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
