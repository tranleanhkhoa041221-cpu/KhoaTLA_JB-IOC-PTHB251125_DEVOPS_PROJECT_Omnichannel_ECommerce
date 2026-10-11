package com.quickbite.edu.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustRequest {

    /** So luong nhap them kho, phai > 0. Dung API rieng cho tang, khong cho giam truc tiep de tranh sai lech voi don hang dang PENDING. */
    @NotNull(message = "So luong khong duoc de trong")
    private Integer quantity;
}
