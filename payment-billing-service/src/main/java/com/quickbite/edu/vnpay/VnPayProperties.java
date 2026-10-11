package com.quickbite.edu.vnpay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binding cho block "vnpay:" trong application.yml.
 * tmnCode + hashSecret la 2 gia tri VNPay cap sau khi dang ky Merchant o
 * moi truong Sandbox: https://sandbox.vnpayment.vn/devreg/
 */
@Component
@ConfigurationProperties(prefix = "vnpay")
@Getter
@Setter
public class VnPayProperties {

    /** vnp_TmnCode - ma website/merchant duoc VNPay cap. */
    private String tmnCode;

    /** Khoa bi mat dung de ky (HMAC-SHA512) va xac thuc chu ky vnp_SecureHash. */
    private String hashSecret;

    /** Cong thanh toan Sandbox nguoi dung se duoc redirect toi. */
    private String payUrl;

    /** Endpoint API server-to-server dung cho querydr/refund. */
    private String apiUrl;

    /** URL VNPay redirect trinh duyet khach ve sau khi thanh toan xong. */
    private String returnUrl;

    /** URL VNPay goi ngam (server-to-server) de bao ket qua giao dich - phai la URL public (vd qua ngrok khi chay local). */
    private String ipnUrl;

    /**
     * Danh sach IP duoc phep goi /ipn (phan tach dau phay). De trong = dung danh sach mac dinh cua VNPay + loopback.
     * Gia tri "*" = TAT kiem tra IP (chi dua vao chu ky HMAC vnp_SecureHash) - CHI dung de test mo phong IPN.
     */
    private String ipnAllowedIps;

    private String version;
    private String command;
    private String currCode;
    private String locale;
    private String orderType;
    private int paymentExpireMinutes;
}
