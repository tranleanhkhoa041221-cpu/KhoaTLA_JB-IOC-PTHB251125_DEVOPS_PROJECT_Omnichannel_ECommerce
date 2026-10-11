package com.quickbite.edu.service.interfaces;

public interface PushNotificationSender {
    void send(Long customerId, String title, String message);
}
