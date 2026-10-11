package com.quickbite.edu.service.impl;

import com.quickbite.edu.config.NotificationProperties;
import com.quickbite.edu.service.interfaces.EmailSender;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoggingEmailSender implements EmailSender {

    private final NotificationProperties properties;
    private final JavaMailSender javaMailSender;

    @Override
    public void send(String to, String subject, String body) {
        if (properties.isMailEnabled()) {
            try {
                MimeMessage message = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(body, false);
                javaMailSender.send(message);
                log.info("[EMAIL-SMTP] Da gui toi {} | {}", to, subject);
                return;
            } catch (Exception ex) {
                throw new IllegalStateException("Gui email SMTP that bai: " + ex.getMessage(), ex);
            }
        }
        log.info("[EMAIL-DEV] to={} | subject={} | body={}", to, subject, body);
    }
}
