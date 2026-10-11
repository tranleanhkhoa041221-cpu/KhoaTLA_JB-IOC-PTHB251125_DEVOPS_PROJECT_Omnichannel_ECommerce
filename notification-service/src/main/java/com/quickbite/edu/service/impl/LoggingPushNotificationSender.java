package com.quickbite.edu.service.impl;

import com.quickbite.edu.service.interfaces.PushNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoggingPushNotificationSender implements PushNotificationSender {

    @Override
    public void send(Long customerId, String title, String message) {
        log.info("[PUSH-DEV] customerId={} | title={} | message={}", customerId, title, message);
    }
}
