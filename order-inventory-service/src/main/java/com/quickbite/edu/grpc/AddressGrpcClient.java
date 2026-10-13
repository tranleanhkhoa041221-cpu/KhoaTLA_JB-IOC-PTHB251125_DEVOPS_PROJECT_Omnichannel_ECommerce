package com.quickbite.edu.grpc;

import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.DownstreamServiceException;
import com.quickbite.edu.exception.NotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AddressGrpcClient {

    private static final long DEADLINE_SECONDS = 3;
    /** Lan thu lai (chi cho loi tam thoi): doc dia chi la thao tac idempotent nen thu lai an toan. */
    private static final long RETRY_DEADLINE_SECONDS = 5;

    private final IdentityServiceGrpc.IdentityServiceBlockingStub identityServiceBlockingStub;

    /**
     * Lấy địa chỉ mặc định của customer từ Identity/User Service qua gRPC.
     * Order Service chỉ nhận dữ liệu cần thiết và lưu thành snapshot trong orders.shipping_address.
     */
    public String getDefaultShippingAddress(Long userId) {
        try {
            GetDefaultAddressRequest request = GetDefaultAddressRequest.newBuilder()
                    .setUserId(userId)
                    .build();

            AddressResponse address = getDefaultAddressWithRetry(request);

            return buildShippingAddress(address);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC getDefaultAddress failed for userId={}", userId, ex);
            switch (ex.getStatus().getCode()) {
                case NOT_FOUND -> throw new NotFoundException(
                        "Customer ID [" + userId + "] chưa có địa chỉ mặc định");
                case INVALID_ARGUMENT -> throw new BadRequestException(ex.getStatus().getDescription());
                // Loi phia SERVER (dich vu Identity cham/khong ket noi duoc) -> 5xx, KHONG phai 400.
                case DEADLINE_EXCEEDED -> throw new DownstreamServiceException(HttpStatus.GATEWAY_TIMEOUT,
                        "Identity/User Service phản hồi quá chậm, vui lòng thử lại sau vài giây");
                default -> throw new DownstreamServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Identity/User Service tạm thời không khả dụng, vui lòng thử lại sau");
            }
        }
    }

    /**
     * Goi gRPC voi deadline 3s; neu timeout/mat ket noi (thuong gap o cuoc goi DAU TIEN sau khi kenh nguoi roi
     * hoac Identity dang khoi dong) thi thu lai DUNG 1 LAN voi deadline 5s truoc khi bao loi.
     */
    private AddressResponse getDefaultAddressWithRetry(GetDefaultAddressRequest request) {
        try {
            return identityServiceBlockingStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getDefaultAddress(request);
        } catch (StatusRuntimeException ex) {
            Status.Code code = ex.getStatus().getCode();
            if (code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.UNAVAILABLE) {
                log.warn("gRPC getDefaultAddress {} - thu lai 1 lan voi deadline {}s", code, RETRY_DEADLINE_SECONDS);
                return identityServiceBlockingStub
                        .withDeadlineAfter(RETRY_DEADLINE_SECONDS, TimeUnit.SECONDS)
                        .getDefaultAddress(request);
            }
            throw ex;
        }
    }

    private String buildShippingAddress(AddressResponse address) {
        return String.join(", ",
                safe(address.getRecipientName()),
                safe(address.getPhone()),
                safe(address.getAddressDetail()),
                safe(address.getWard()),
                safe(address.getDistrict()),
                safe(address.getProvince())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
