package com.quickbite.edu.vnpay;

import com.quickbite.edu.entity.Payment;
import com.quickbite.edu.exception.PaymentGatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trien khai chuan theo tai lieu tich hop VNPay Sandbox:
 * - buildPaymentUrl: giao tiep bang URL redirect (GET), ky HMAC-SHA512 tren query string.
 * - queryTransaction / refund: goi API server-to-server (POST JSON) toi Merchant Webapi,
 *   ky HMAC-SHA512 tren chuoi noi bang dau "|" theo dung thu tu tai lieu quy dinh.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VnPayServiceImpl implements VnPayService {

    private final VnPayProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String buildPaymentUrl(Payment payment, String clientIp) {
        String createDate = payment.getVnpCreateDate() != null ? payment.getVnpCreateDate() : VnPayUtils.now();
        LocalDateTime expireAt = VnPayUtils.nowVn().plusMinutes(props.getPaymentExpireMinutes());

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", props.getVersion());
        params.put("vnp_Command", props.getCommand());
        params.put("vnp_TmnCode", props.getTmnCode());
        params.put("vnp_Amount", toVnpAmount(payment.getAmount()));
        params.put("vnp_CurrCode", props.getCurrCode());
        params.put("vnp_TxnRef", payment.getVnpTxnRef());
        params.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderCode());
        params.put("vnp_OrderType", props.getOrderType());
        params.put("vnp_Locale", props.getLocale());
        params.put("vnp_ReturnUrl", props.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", VnPayUtils.format(expireAt));

        VnPayUtils.HashAndQuery hashAndQuery = VnPayUtils.buildHashAndQuery(params);
        String secureHash = VnPayUtils.hmacSHA512(props.getHashSecret(), hashAndQuery.hashData());
        return props.getPayUrl() + "?" + hashAndQuery.query() + "&vnp_SecureHash=" + secureHash;
    }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        return VnPayUtils.verifySignature(params, props.getHashSecret());
    }

    @Override
    public Map<String, String> queryTransaction(Payment payment, String clientIp) {
        String requestId = VnPayUtils.generateRequestId();
        String createDate = VnPayUtils.now();

        Map<String, String> data = new LinkedHashMap<>();
        data.put("vnp_RequestId", requestId);
        data.put("vnp_Version", props.getVersion());
        data.put("vnp_Command", "querydr");
        data.put("vnp_TmnCode", props.getTmnCode());
        data.put("vnp_TxnRef", payment.getVnpTxnRef());
        data.put("vnp_OrderInfo", "Doi soat giao dich " + payment.getOrderCode());
        data.put("vnp_TransactionDate", payment.getVnpCreateDate());
        data.put("vnp_CreateDate", createDate);
        data.put("vnp_IpAddr", clientIp);

        String signData = String.join("|",
                requestId, props.getVersion(), "querydr", props.getTmnCode(),
                payment.getVnpTxnRef(), payment.getVnpCreateDate(), createDate, clientIp,
                data.get("vnp_OrderInfo"));
        data.put("vnp_SecureHash", VnPayUtils.hmacSHA512(props.getHashSecret(), signData));

        return callMerchantApi(data, "querydr");
    }

    @Override
    public Map<String, String> refund(Payment payment, BigDecimal refundAmount, String reason, String createBy, String clientIp) {
        String requestId = VnPayUtils.generateRequestId();
        String createDate = VnPayUtils.now();
        String transactionType = refundAmount.compareTo(payment.getAmount()) >= 0 ? "02" : "03"; // 02: toan phan, 03: mot phan
        String amountStr = toVnpAmount(refundAmount);
        String createByValue = (createBy == null || createBy.isBlank()) ? "SYSTEM" : createBy;

        Map<String, String> data = new LinkedHashMap<>();
        data.put("vnp_RequestId", requestId);
        data.put("vnp_Version", props.getVersion());
        data.put("vnp_Command", "refund");
        data.put("vnp_TmnCode", props.getTmnCode());
        data.put("vnp_TransactionType", transactionType);
        data.put("vnp_TxnRef", payment.getVnpTxnRef());
        data.put("vnp_Amount", amountStr);
        data.put("vnp_OrderInfo", "Hoan tien don hang " + payment.getOrderCode() + ": " + reason);
        data.put("vnp_TransactionNo", payment.getVnpTransactionNo() != null ? payment.getVnpTransactionNo() : "0");
        data.put("vnp_TransactionDate", payment.getVnpCreateDate());
        data.put("vnp_CreateBy", createByValue);
        data.put("vnp_CreateDate", createDate);
        data.put("vnp_IpAddr", clientIp);

        String signData = String.join("|",
                requestId, props.getVersion(), "refund", props.getTmnCode(), transactionType,
                payment.getVnpTxnRef(), amountStr, data.get("vnp_TransactionNo"), payment.getVnpCreateDate(),
                createByValue, createDate, clientIp, data.get("vnp_OrderInfo"));
        data.put("vnp_SecureHash", VnPayUtils.hmacSHA512(props.getHashSecret(), signData));

        return callMerchantApi(data, "refund");
    }

    private Map<String, String> callMerchantApi(Map<String, String> body, String command) {
        try {
            // Tu serialize bang ObjectMapper (tools.jackson) da inject thay vi dua vao
            // HttpMessageConverter mac dinh cua RestTemplate - tranh phu thuoc vao viec
            // RestTemplate co tu dong nhan dien duoc Jackson 3 hay khong.
            String requestJson = objectMapper.writeValueAsString(body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(props.getApiUrl(), entity, String.class);
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new PaymentGatewayException("VNPay khong tra ve du lieu cho lenh " + command);
            }

            Map<String, Object> raw = objectMapper.readValue(responseBody, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(key, value == null ? null : String.valueOf(value)));
            return result;
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Goi VNPay Merchant API [{}] that bai", command, ex);
            throw new PaymentGatewayException("Khong the ket noi toi VNPay de thuc hien lenh " + command);
        }
    }

    private String toVnpAmount(BigDecimal amount) {
        return String.valueOf(amount.multiply(BigDecimal.valueOf(100)).longValue());
    }
}
