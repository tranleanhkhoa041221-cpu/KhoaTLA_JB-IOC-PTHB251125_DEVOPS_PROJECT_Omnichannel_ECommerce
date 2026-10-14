package com.quickbite.edu.service.impl;

import com.quickbite.edu.config.NotificationProperties;
import com.quickbite.edu.service.interfaces.SmsSender;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gui SMS qua Twilio Programmable Messaging API (https://www.twilio.com/docs/sms).
 * Neu notification.sms-enabled=false (mac dinh khi chua co Twilio credentials that),
 * chi ghi log ra console - phu hop dev/demo khong can tai khoan that.
 *
 * LUU Y VE TAI KHOAN TWILIO TRIAL (mien phi):
 * - Chi gui duoc SMS toi cac so da "Verify" trong Console (Phone Numbers > Manage >
 *   Verified Caller IDs). Gui toi so chua verify se bi Twilio tu choi voi loi 21608/21211.
 * - Moi tin nhan gui di se tu dong bi Twilio chen them tien to "Sent from your Twilio
 *   trial account - " vao dau noi dung, do la gioi han cua tai khoan trial, khong phai loi code.
 * - So dien thoai nguoi nhan (recipient) BAT BUOC dinh dang E.164 (vd +84901234567),
 *   nen ham send() ben duoi tu dong chuan hoa so Viet Nam dang "090..."/"0901234567"
 *   ve "+8490...", ban co the mo rong normalize() neu can ho tro dau so quoc gia khac.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioSmsSender implements SmsSender {

    private final NotificationProperties properties;
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        if (properties.isSmsEnabled()) {
            if (isBlank(properties.getTwilioAccountSid()) || isBlank(properties.getTwilioAuthToken())) {
                log.warn("notification.sms-enabled=true nhung thieu twilio-account-sid/twilio-auth-token "
                        + "- SMS se bi FALLBACK ve log console cho den khi cau hinh du 2 gia tri nay.");
                return;
            }
            Twilio.init(properties.getTwilioAccountSid(), properties.getTwilioAuthToken());
            initialized = true;
            log.info("Da khoi tao Twilio SDK thanh cong voi Account SID={}...",
                    properties.getTwilioAccountSid().substring(0, Math.min(8, properties.getTwilioAccountSid().length())));
        }
    }

    @Override
    public void send(String phoneNumber, String message) {
        if (properties.isSmsEnabled() && initialized) {
            try {
                String to = normalizeToE164(phoneNumber);
                Message sms = Message.creator(
                        new PhoneNumber(to),
                        new PhoneNumber(properties.getTwilioFromNumber()),
                        message
                ).create();
                log.info("[SMS-TWILIO] Da gui toi {} | sid={} | status={}", to, sms.getSid(), sms.getStatus());
                return;
            } catch (Exception ex) {
                // KHONG nuot loi: nem len de dispatch() o NotificationServiceImpl ghi nhan
                // NotificationStatus.FAILED + errorMessage vao notification_logs.
                throw new IllegalStateException("Gui SMS qua Twilio that bai: " + ex.getMessage(), ex);
            }
        }
        log.info("[SMS-DEV] phone={} | message={}", phoneNumber, message);
    }

    /**
     * Chuan hoa so dien thoai Viet Nam ve dinh dang E.164 ma Twilio yeu cau bat buoc.
     * "0901234567" -> "+84901234567"; da la "+84..." thi giu nguyen.
     */
    private String normalizeToE164(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("So dien thoai nguoi nhan bi trong");
        }
        String digits = phoneNumber.trim();
        if (digits.startsWith("+")) {
            return digits;
        }
        digits = digits.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            return "+84" + digits.substring(1);
        }
        if (digits.startsWith("84")) {
            return "+" + digits;
        }
        return "+" + digits;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
