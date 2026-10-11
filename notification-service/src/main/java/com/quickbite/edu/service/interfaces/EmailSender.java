package com.quickbite.edu.service.interfaces;

public interface EmailSender {
    void send(String to, String subject, String body);
}
