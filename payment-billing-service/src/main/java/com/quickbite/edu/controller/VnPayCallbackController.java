package com.quickbite.edu.controller;

import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.dto.response.PaymentResponse;
import com.quickbite.edu.service.interfaces.PaymentService;
import com.quickbite.edu.vnpay.VnPayProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Enumeration;
import java.util.Map;

/**
 * 2 endpoint public (KHONG qua Authentication cua API Gateway) ma VNPay se
 * goi/redirect toi:
 *  - GET /api/payments/vnpay/return -> trinh duyet khach hang duoc VNPay
 *    redirect ve sau khi thanh toan xong (vnp_ReturnUrl). Chi phuc vu UX,
 *    KHONG duoc dung lam nguon xac nhan thanh toan chinh thuc vi du lieu
 *    di qua trinh duyet cua khach (co the bi can thiep).
 *  - GET /api/payments/vnpay/ipn -> VNPay goi NGAM server-to-server
 *    (vnp_IpnUrl) bao ket qua giao dich that su - day moi la nguon DUY NHAT
 *    dang tin cay de xac nhan thanh toan (co verify vnp_SecureHash).
 *    Luu y: URL nay phai la PUBLIC (VD dung ngrok khi chay local) thi VNPay
 *    moi goi toi duoc; neu chi test tren localhost/Postman thi dung tam
 *    endpoint /return o tren de mo phong ket qua.
 */
@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
@Slf4j
public class VnPayCallbackController {

    private final PaymentService paymentService;
    private final VnPayProperties vnPayProperties;

    @GetMapping("/return")
    public ResponseEntity<ApiResponse<PaymentResponse>> returnUrl(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        log.info("Nhan VNPay return callback: {}", params);
        PaymentResponse response = paymentService.handleReturnCallback(params);
        String message = "SUCCESS".equals(response.getStatus()) ? "Thanh toan thanh cong" : "Da ghi nhan ket qua thanh toan";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    // Danh sach IP mac dinh cua VNPay theo tai lieu tich hop. Co the ghi de bang bien moi truong
    // VNPAY_IPN_ALLOWED_IPS (phan tach dau phay; "*" = tat kiem tra IP, chi con kiem tra chu ky HMAC).
    private static final java.util.List<String> DEFAULT_VNPAY_IPS = java.util.Arrays.asList(
            "113.160.92.202", "113.52.45.78", "113.52.45.79", "113.160.92.203", "113.52.45.86", "127.0.0.1", "0:0:0:0:0:0:0:1"
    );

    private boolean isIpAllowed(String clientIp) {
        String configured = vnPayProperties.getIpnAllowedIps();
        if (configured == null || configured.isBlank()) {
            return DEFAULT_VNPAY_IPS.contains(clientIp);
        }
        if ("*".equals(configured.trim())) {
            return true;
        }
        return Arrays.stream(configured.split(",")).map(String::trim).anyMatch(clientIp::equals);
    }

    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> ipnCallback(HttpServletRequest request) {
        String clientIp = com.quickbite.edu.vnpay.VnPayUtils.getClientIpAddress(request);
        if (!isIpAllowed(clientIp)) {
            log.warn("IP {} khong thuoc danh sach IP duoc phep goi IPN (xem VNPAY_IPN_ALLOWED_IPS)", clientIp);
            Map<String, String> error = new LinkedHashMap<>();
            error.put("RspCode", "99");
            error.put("Message", "Unknown error");
            return ResponseEntity.ok(error);
        }

        Map<String, String> params = extractParams(request);
        log.info("Nhan VNPay IPN callback: {}", params);
        Map<String, String> result = paymentService.handleIpnCallback(params);
        return ResponseEntity.ok(result);
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }
}
