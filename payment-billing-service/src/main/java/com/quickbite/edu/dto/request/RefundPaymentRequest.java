package com.quickbite.edu.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundPaymentRequest {

    @NotNull(message = "So tien hoan khong duoc de trong")
    @DecimalMin(value = "0.01", message = "So tien hoan phai lon hon 0")
    private BigDecimal amount;

    @NotBlank(message = "Ly do hoan tien khong duoc de trong")
    private String reason;

    /** Nguoi thuc hien thao tac hoan tien (vd: admin username) - phuc vu vnp_CreateBy. */
    private String createBy;
}
