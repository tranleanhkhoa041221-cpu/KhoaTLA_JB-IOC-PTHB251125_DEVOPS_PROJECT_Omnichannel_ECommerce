package com.quickbite.edu.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentUrlResponse {
    /** URL day du (da ky vnp_SecureHash) - client (FE) chi can redirect trinh duyet toi day. */
    private String paymentUrl;
    private String vnpTxnRef;
    private LocalDateTime expireAt;
}
