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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gui tin nhan dien thoai qua Twilio, ho tro 3 che do (chon bang notification.twilio-channel va twilio-whatsapp-content-sid):
 *  1. sms (mac dinh): Twilio Programmable Messaging, Body tu do.
 *  2. whatsapp + KHONG co content-sid: Body tu do qua WhatsApp (to/from co tien to "whatsapp:"). Chi dung duoc voi
 *     sender cho phep Body; sender chi cho template se bi Twilio tu choi ngay voi loi 21654 "ContentSid Required".
 *  3. whatsapp + co content-sid (HX...): gui bang WhatsApp TEMPLATE (ContentSid + ContentVariables) qua REST API cua
 *     Twilio. Goi thang REST (java.net.http) thay vi SDK de khong phu thuoc overload cua SDK 10.x.
 *
 * LUU Y:
 *  - Twilio tra "queued" ngay ca khi sau do giao that bai (vd 63016 ngoai cua so 24h), nen "SENT" trong notification_logs
 *    chua dam bao tin da toi may; xem trang thai that tai Twilio Console > Monitor > Logs > Messaging.
 *  - Bien cua WhatsApp template khong duoc chua xuong dong/tab va khong duoc co nhieu dau cach lien tiep:
 *    ham sanitizeForTemplate() tu lam sach noi dung truoc khi dua vao {{1}}.
 *  - Tai khoan trial chi gui toi so da duoc phep; SMS toi so Viet Nam co the bi han che theo tai khoan.
 *  - So dien thoai BAT BUOC dinh dang E.164: normalizeToE164() doi "0971820454" thanh "+84971820454".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioSmsSender implements SmsSender {

    private static final String MESSAGES_URL = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";
    private static final String DEFAULT_CONTENT_VARIABLES = "{\"1\":\"{{message}}\"}";
    private static final int MAX_TEMPLATE_VARIABLE_LENGTH = 900;
    private static final Pattern SID = Pattern.compile("\"sid\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern MSG_ERROR_CODE = Pattern.compile("\"error_code\"\\s*:\\s*(\\d+)");
    /** Ma loi Twilio: freeform ngoai cua so 24h. */
    private static final String OUTSIDE_WINDOW = "63016";
    private static final int STATUS_POLL_TRIES = 4;
    private static final long STATUS_POLL_MS = 1000L;

    private final NotificationProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
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
            log.info("Kenh gui tin nhan dien thoai: {}", describeMode());
            log.info("Da khoi tao Twilio SDK thanh cong voi Account SID={}...",
                    properties.getTwilioAccountSid().substring(0, Math.min(8, properties.getTwilioAccountSid().length())));
        }
    }

    @Override
    public void send(String phoneNumber, String message) {
        if (properties.isSmsEnabled() && initialized) {
            boolean whatsapp = isWhatsApp();
            String label = whatsapp ? "WHATSAPP" : "SMS";
            try {
                String to = normalizeToE164(phoneNumber, "nguoi nhan");
                String from = normalizeToE164(properties.getTwilioFromNumber(), "so gui (TWILIO_FROM_NUMBER)");
                if (whatsapp) {
                    to = "whatsapp:" + to;
                    from = "whatsapp:" + from;
                    if (!isBlank(properties.getTwilioWhatsappContentSid())) {
                        sendWhatsAppFreeformThenTemplate(to, from, message);
                        return;
                    }
                }
                Message sms = Message.creator(new PhoneNumber(to), new PhoneNumber(from), message).create();
                log.info("[{}-TWILIO] Da gui toi {} | sid={} | status={}", label, to, sms.getSid(), sms.getStatus());
                return;
            } catch (Exception ex) {
                // KHONG nuot loi: nem len de dispatch() o NotificationServiceImpl ghi nhan
                // NotificationStatus.FAILED + errorMessage vao notification_logs.
                throw new IllegalStateException("Gui " + label + " qua Twilio that bai: " + ex.getMessage(), ex);
            }
        }
        log.info("[SMS-DEV] phone={} | message={}", phoneNumber, message);
    }

    /**
     * Uu tien gui NOI DUNG THAT (Body tu do). Chi khi Twilio bao ngoai cua so 24h (63016) moi gui TEMPLATE co dinh.
     * 63016 co the tra ve ngay (HTTP 400) hoac chi hien sau khi message da "queued" -> sau khi gui, doi toi da ~4s
     * va kiem tra lai trang thai; neu failed/undelivered voi 63016 thi gui template.
     */
    private void sendWhatsAppFreeformThenTemplate(String to, String from, String message) throws Exception {
        String form = "To=" + enc(to) + "&From=" + enc(from) + "&Body=" + enc(message);
        HttpResponse<String> response = postMessage(form);
        String body = response.body() == null ? "" : response.body();
        if (response.statusCode() / 100 != 2) {
            String code = first(ERROR_CODE, body);
            if (OUTSIDE_WINDOW.equals(code)) {
                log.warn("[WHATSAPP-TWILIO] Ngoai cua so 24h (63016) - gui TEMPLATE co dinh thay the (khong co noi dung don hang).");
                sendWhatsAppTemplate(to, from, message);
                return;
            }
            throw new IllegalStateException("Twilio HTTP " + response.statusCode() + " (ma loi " + code + "): "
                    + first(ERROR_MESSAGE, body));
        }
        String sid = first(SID, body);
        String status = first(STATUS, body);
        for (int i = 0; i < STATUS_POLL_TRIES && !"?".equals(sid); i++) {
            if (isFinalStatus(status)) {
                break;
            }
            Thread.sleep(STATUS_POLL_MS);
            String current = fetchMessage(sid);
            status = first(STATUS, current);
            if ("failed".equals(status) || "undelivered".equals(status)) {
                String code = first(MSG_ERROR_CODE, current);
                if (OUTSIDE_WINDOW.equals(code)) {
                    log.warn("[WHATSAPP-TWILIO] sid={} bi tu choi 63016 (ngoai cua so 24h) - gui TEMPLATE co dinh thay the.", sid);
                    sendWhatsAppTemplate(to, from, message);
                    return;
                }
                throw new IllegalStateException("Twilio WhatsApp " + status + " (ma loi " + code + ") sid=" + sid);
            }
        }
        log.info("[WHATSAPP-TWILIO] Da gui noi dung thong bao toi {} | sid={} | status={}", to, sid, status);
    }

    private static boolean isFinalStatus(String status) {
        return "sent".equals(status) || "delivered".equals(status) || "read".equals(status);
    }

    private HttpResponse<String> postMessage(String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(MESSAGES_URL, properties.getTwilioAccountSid())))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basicAuth())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String fetchMessage(String sid) throws Exception {
        String url = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages/%s.json",
                properties.getTwilioAccountSid(), sid);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + basicAuth())
                .GET().build();
        HttpResponse<String> r = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return r.body() == null ? "" : r.body();
    }

    private String basicAuth() {
        return Base64.getEncoder().encodeToString(
                (properties.getTwilioAccountSid() + ":" + properties.getTwilioAuthToken()).getBytes(StandardCharsets.UTF_8));
    }

    /** Gui WhatsApp template (ContentSid + ContentVariables) bang REST API cua Twilio. */
    private void sendWhatsAppTemplate(String to, String from, String message) throws Exception {
        String form = "To=" + enc(to)
                + "&From=" + enc(from)
                + "&ContentSid=" + enc(properties.getTwilioWhatsappContentSid().trim())
                + "&ContentVariables=" + enc(buildContentVariables(message));
        String auth = Base64.getEncoder().encodeToString(
                (properties.getTwilioAccountSid() + ":" + properties.getTwilioAuthToken()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(MESSAGES_URL, properties.getTwilioAccountSid())))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body() == null ? "" : response.body();
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Twilio HTTP " + response.statusCode() + " (ma loi " + first(ERROR_CODE, body)
                    + "): " + first(ERROR_MESSAGE, body));
        }
        log.info("[WHATSAPP-TEMPLATE-TWILIO] Da gui toi {} | sid={} | status={}", to, first(SID, body), first(STATUS, body));
    }

    /** Thay {{message}} trong mau ContentVariables bang noi dung thong bao da lam sach va escape JSON. */
    private String buildContentVariables(String message) {
        String template = isBlank(properties.getTwilioWhatsappContentVariables())
                ? DEFAULT_CONTENT_VARIABLES
                : properties.getTwilioWhatsappContentVariables().trim();
        return template.replace("{{message}}", jsonEscape(sanitizeForTemplate(message)));
    }

    /** Bien WhatsApp template khong duoc co xuong dong/tab va khong duoc co nhieu dau cach lien tiep. */
    static String sanitizeForTemplate(String message) {
        if (message == null) {
            return "";
        }
        String cleaned = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").strip();
        return cleaned.length() > MAX_TEMPLATE_VARIABLE_LENGTH ? cleaned.substring(0, MAX_TEMPLATE_VARIABLE_LENGTH) : cleaned;
    }

    static String jsonEscape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String first(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : "?";
    }

    private boolean isWhatsApp() {
        return "whatsapp".equalsIgnoreCase(properties.getTwilioChannel());
    }

    private String describeMode() {
        if (!isWhatsApp()) {
            return "SMS";
        }
        return isBlank(properties.getTwilioWhatsappContentSid()) ? "WHATSAPP (Body tu do)" : "WHATSAPP (noi dung that, fallback template khi qua 24h)";
    }

    /**
     * Chuan hoa so dien thoai Viet Nam ve dinh dang E.164 ma Twilio yeu cau bat buoc.
     * "0901234567" -> "+84901234567"; da la "+84..." thi giu nguyen; tien to "whatsapp:" (neu co) duoc bo di.
     */
    private String normalizeToE164(String phoneNumber, String what) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("So dien thoai " + what + " bi trong");
        }
        String digits = phoneNumber.trim();
        if (digits.regionMatches(true, 0, "whatsapp:", 0, 9)) {
            digits = digits.substring(9).trim();
        }
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