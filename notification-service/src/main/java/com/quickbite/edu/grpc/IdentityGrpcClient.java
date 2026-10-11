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
public class IdentityGrpcClient {

    private static final long DEADLINE_SECONDS = 3;

    private final IdentityServiceGrpc.IdentityServiceBlockingStub identityServiceBlockingStub;

    public com.quickbite.edu.dto.client.UserContactResponse getUserContact(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("customerId không hợp lệ");
        }

        try {
            com.quickbite.edu.grpc.UserContactResponse response = identityServiceBlockingStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getUserContact(GetUserContactRequest.newBuilder()
                            .setUserId(userId)
                            .build());

            return com.quickbite.edu.dto.client.UserContactResponse.builder()
                    .id(response.getId())
                    .username(emptyToNull(response.getUsername()))
                    .email(emptyToNull(response.getEmail()))
                    .fullName(emptyToNull(response.getFullName()))
                    .phone(emptyToNull(response.getPhone()))
                    .build();
        } catch (StatusRuntimeException ex) {
            log.error("gRPC getUserContact failed for userId={}", userId, ex);
            switch (ex.getStatus().getCode()) {
                case NOT_FOUND -> throw new NotFoundException(
                        "Không tìm thấy người dùng với ID: " + userId);
                case INVALID_ARGUMENT -> throw new BadRequestException(
                        ex.getStatus().getDescription());
                case DEADLINE_EXCEEDED -> throw new BadRequestException(
                        "Identity/User Service phản hồi quá chậm (quá " + DEADLINE_SECONDS + "s), vui lòng thử lại");
                default -> throw new BadRequestException(
                        "Không thể kết nối đến Identity/User Service: " + ex.getStatus().getDescription());
            }
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
