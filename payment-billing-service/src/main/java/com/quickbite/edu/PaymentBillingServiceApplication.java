package com.quickbite.edu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // can cho OutboxEventPublisher (Outbox Pattern) chay dinh ky
public class PaymentBillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentBillingServiceApplication.class, args);
    }

}
