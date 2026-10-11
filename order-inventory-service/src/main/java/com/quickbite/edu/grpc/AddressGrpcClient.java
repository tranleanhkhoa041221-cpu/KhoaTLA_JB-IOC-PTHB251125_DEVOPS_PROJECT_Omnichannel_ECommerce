package com.quickbite.edu.grpc;

import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.NotFoundException;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AddressGrpcClient {

    private static final long DEADLINE_SECONDS = 3;

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

            AddressResponse address = identityServiceBlockingStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getDefaultAddress(request);

            return buildShippingAddress(address);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC getDefaultAddress failed for userId={}", userId, ex);
            switch (ex.getStatus().getCode()) {
                case NOT_FOUND -> throw new NotFoundException(
                        "Customer ID [" + userId + "] chưa có địa chỉ mặc định");
                case INVALID_ARGUMENT -> throw new BadRequestException(ex.getStatus().getDescription());
                case DEADLINE_EXCEEDED -> throw new BadRequestException(
                        "Identity/User Service phản hồi quá chậm (quá " + DEADLINE_SECONDS + "s), vui lòng thử lại");
                default -> throw new BadRequestException(
                        "Không thể kết nối đến Identity/User Service: " + ex.getStatus().getDescription());
            }
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
