package com.quickbite.edu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"order.created", "order.cancelled", "payment.completed", "payment.failed"})
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
