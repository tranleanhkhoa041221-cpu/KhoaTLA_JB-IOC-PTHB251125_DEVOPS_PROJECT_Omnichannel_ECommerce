package com.quickbite.edu.grpc;

import com.quickbite.edu.dto.ProductStockInfo;
import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.NotFoundException;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * Client goi sang Product Catalog Service qua gRPC (dong bo, do tre thap)
 * de kiem tra gia + ton kho hien thi truoc khi Order Service tien hanh
 * giu (reserve) kho noi bo cua chinh no.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductGrpcClient {

    /**
     * Performance/Scalability (sua ngay 2026-10): TRUOC DAY goi blocking stub KHONG co deadline
     * nao ca - neu Product Catalog Service bi cham/treo (vd dang nghen o tang DB connection pool
     * cua chinh no luc Flash Sale), call nay co the BLOCK VO THOI HAN, giu luon 1 Tomcat worker
     * thread cua order-inventory-service (goi TRUOC khi vao Redis lock/transaction, nen 1 call
     * treo = 1 thread HTTP bi chiem dung, cong don voi so thread dang bi RedisLockService giu se
     * lam can kiet thread pool nhanh hon nhieu). Them deadline huu han de fail-fast: loi tra ve
     * cho client ro rang (409/400) thay vi client tu timeout vo thoi han phia Gateway/trinh duyet.
     */
    private static final long DEADLINE_SECONDS = 3;

    private final ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub;

    public ProductStockInfo checkStockAndPrice(String sku, int quantity) {
        try {
            CheckStockRequest request = CheckStockRequest.newBuilder()
                    .setSku(sku)
                    .setQuantity(quantity)
                    .build();

            CheckStockResponse response = productServiceBlockingStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .checkStockAndPrice(request);

            return ProductStockInfo.builder()
                    .available(response.getAvailable())
                    .price(BigDecimal.valueOf(response.getPrice()))
                    .productName(response.getProductName())
                    .message(response.getMessage())
                    .build();
        } catch (StatusRuntimeException ex) {
            log.error("gRPC call checkStockAndPrice failed for sku={}", sku, ex);
            switch (ex.getStatus().getCode()) {
                case NOT_FOUND -> throw new NotFoundException("Khong tim thay san pham SKU [" + sku + "] tren Product Catalog Service");
                case INVALID_ARGUMENT -> throw new BadRequestException(ex.getStatus().getDescription());
                case DEADLINE_EXCEEDED -> throw new BadRequestException(
                        "Product Catalog Service phan hoi qua cham (qua " + DEADLINE_SECONDS + "s), vui long thu lai");
                default -> throw new BadRequestException("Khong the ket noi den Product Catalog Service: " + ex.getStatus().getDescription());
            }
        }
    }
}
