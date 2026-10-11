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

    private String fallbackEmailDomain = "quickbite.local";
}
