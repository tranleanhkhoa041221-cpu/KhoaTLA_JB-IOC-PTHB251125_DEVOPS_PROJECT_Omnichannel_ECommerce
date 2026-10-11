package com.quickbite.edu.config;

import com.quickbite.edu.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Khai bao tuong minh so partition cho cac topic do Payment & Billing Service PHAT ra
 * (payment.completed, payment.failed). Xem giai thich chi tiet trong KafkaTopicConfig cua
 * order-inventory-service (cung ly do: tranh Kafka auto-create topic voi 1 partition mac dinh,
 * gay nghen throughput khi Order & Inventory Service (consumer cua 2 topic nay) can xu ly
 * dong thoi nhieu ket qua thanh toan luc Flash Sale).
 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
