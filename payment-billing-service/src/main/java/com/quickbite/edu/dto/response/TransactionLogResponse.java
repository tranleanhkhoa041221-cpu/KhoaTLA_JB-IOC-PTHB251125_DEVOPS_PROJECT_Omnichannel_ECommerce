package com.quickbite.edu.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLogResponse {
    private Long id;
    private Long paymentId;
    private String type;
    private String requestParams;
    private String responseParams;
    private String responseCode;
    private String message;
    private LocalDateTime createdAt;
}
