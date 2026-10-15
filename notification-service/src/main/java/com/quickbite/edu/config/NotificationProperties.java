package com.quickbite.edu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /** Bat SMTP that; false = chi ghi log (phu hop dev/demo). */
    private boolean mailEnabled;

    /** Bat Twilio SMS that; false = chi ghi log (phu hop dev/demo hoac khi chua co tai khoan Twilio). */
    private boolean smsEnabled;

    /** Twilio Account SID - lay tai console.twilio.com (trang "Ahoy, ..."), o o "SID tai khoan". */
    private String twilioAccountSid;

    /** Twilio Auth Token - cung trang, o "Ma xac thuc" (bam icon con mat de hien). */
    private String twilioAuthToken;

    /**
     * So dien thoai Twilio dung de gui SMS (dinh dang E.164, vd +15017122661) - lay tai
     * Console > Phone Numbers > Manage > Active numbers. Tai khoan Twilio trial se tu cap 1 so mien phi.
     */
    private String twilioFromNumber;

    /**
     * Kenh Twilio dung cho thong bao "dien thoai": "sms" (mac dinh) hoac "whatsapp".
     * Log trong notification_logs van ghi kenh SMS (chi doi nha cung cap gui).
     */
    private String twilioChannel = "sms";

    /**
     * Content SID (HX...) cua WhatsApp template. Co gia tri = gui bang TEMPLATE (ContentSid + ContentVariables):
     * BAT BUOC voi sender WhatsApp khong cho gui Body tu do (Twilio tra loi 21654 "ContentSid Required")
     * va voi moi tin ngoai cua so 24h. De trong = gui Body tu do (chi dung duoc voi sender cho phep).
     */
    private String twilioWhatsappContentSid;

    /**
     * Mau ContentVariables (JSON). Chuoi {{message}} duoc thay bang noi dung thong bao (da lam sach, escape JSON).
     * De trong = {"1":"{{message}}"} (template co DUNG 1 bien {{1}}). Vi du template mau 2 bien:
     * {"1":"{{message}}","2":"QuickBite"}.
     */
    private String twilioWhatsappContentVariables;

    private String fallbackEmailDomain = "quickbite.local";
}
