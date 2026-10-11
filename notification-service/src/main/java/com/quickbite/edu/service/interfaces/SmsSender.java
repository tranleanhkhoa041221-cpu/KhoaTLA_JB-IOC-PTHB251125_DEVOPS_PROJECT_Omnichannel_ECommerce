package com.quickbite.edu.service.interfaces;

public interface SmsSender {
    void send(String phoneNumber, String message);
}
