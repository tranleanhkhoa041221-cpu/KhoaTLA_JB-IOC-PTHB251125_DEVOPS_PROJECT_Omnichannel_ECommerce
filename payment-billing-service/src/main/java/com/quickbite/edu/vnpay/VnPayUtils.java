package com.quickbite.edu.vnpay;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ham thuan (khong giu state) trien khai dung theo tai lieu ky thuat chinh
 * thuc cua VNPay: sap xep tham so theo alphabet (TreeMap), noi thanh chuoi
 * "key=value" cach nhau boi "&", ky bang HMAC-SHA512 voi vnp_HashSecret.
 * Tham khao: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
 */
public final class VnPayUtils {

    public static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private VnPayUtils() {
    }

    /** Ky HMAC-SHA512, tra ve chuoi hex thuong - dung lam vnp_SecureHash. */
    public static String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Khong the tao chu ky VNPay (HMAC-SHA512)", ex);
        }
    }

    /**
     * Sap xep params theo key (bo qua key rong/null va vnp_SecureHash*), sinh dong thoi:
     * - hashData: dung de tinh vnp_SecureHash
     * - query:    dung de ghep thanh URL redirect hoan chinh
     */
    public static HashAndQuery buildHashAndQuery(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.remove("vnp_SecureHash");
        sorted.remove("vnp_SecureHashType");

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        int i = 0;
        int size = sorted.size();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
                i++;
                if (i < size) {
                    hashData.append('&');
                    query.append('&');
                }
            }
        }
        return new HashAndQuery(hashData.toString(), query.toString());
    }

    /** Tinh lai chu ky tren toan bo params (loai vnp_SecureHash) roi so sanh voi vnp_SecureHash nhan duoc. */
    public static boolean verifySignature(Map<String, String> params, String hashSecret) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        String calculatedHash = hmacSHA512(hashSecret, buildHashAndQuery(params).hashData());
        return calculatedHash.equalsIgnoreCase(receivedHash);
    }

    /** Lay IP that cua client, uu tien header X-Forwarded-For (khi chay sau Nginx/API Gateway). */
    public static String getClientIpAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String ip = request.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }

    /**
     * VNPay yeu cau MOI thoi gian (vnp_CreateDate, vnp_ExpireDate, vnp_TransactionDate...) theo GMT+7.
     * Container tren VPS chay UTC -> LocalDateTime.now() mac dinh lech 7 gio -> vnp_ExpireDate nam trong QUA KHU
     * (theo gio VNPay) -> trang thanh toan bao "giao dich het han" (ma loi 15). Vi vay LUON lay gio theo mui gio VN.
     */
    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static LocalDateTime nowVn() {
        return LocalDateTime.now(VN_ZONE);
    }

    public static String now() {
        return nowVn().format(VNPAY_DATE_FORMAT);
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime.format(VNPAY_DATE_FORMAT);
    }

    public static LocalDateTime parse(String vnpDate) {
        return LocalDateTime.parse(vnpDate, VNPAY_DATE_FORMAT);
    }

    /** vnp_RequestId cho cac API server-to-server (querydr/refund) - chi can duy nhat moi lan goi. */
    public static String generateRequestId() {
        return Long.toString(System.currentTimeMillis()) + (100 + new SecureRandom().nextInt(900));
    }

    public record HashAndQuery(String hashData, String query) {
    }
}
