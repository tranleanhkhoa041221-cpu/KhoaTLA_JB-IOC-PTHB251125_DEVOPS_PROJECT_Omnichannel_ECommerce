package com.quickbite.edu.grpc;

import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.DownstreamServiceException;
import com.quickbite.edu.exception.NotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityGrpcClient {

    private static final long DEADLINE_SECONDS = 3;
    /** Lan thu lai (chi cho loi tam thoi): lay thong tin lien he user la thao tac doc, idempotent. */
    private static final long RETRY_DEADLINE_SECONDS = 5;

    private final IdentityServiceGrpc.IdentityServiceBlockingStub identityServiceBlockingStub;

    public com.quickbite.edu.dto.client.UserContactResponse getUserContact(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("customerId không hợp lệ");
        }

        try {
            com.quickbite.edu.grpc.UserContactResponse response = getUserContactWithRetry(
                    GetUserContactRequest.newBuilder().setUserId(userId).build());

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
                // Loi phia SERVER (Identity cham/khong ket noi duoc) -> 5xx, KHONG phai 400.
                case DEADLINE_EXCEEDED -> throw new DownstreamServiceException(HttpStatus.GATEWAY_TIMEOUT,
                        "Identity/User Service phản hồi quá chậm (quá " + RETRY_DEADLINE_SECONDS + "s), vui lòng thử lại sau");
                default -> throw new DownstreamServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Identity/User Service tạm thời không khả dụng, vui lòng thử lại sau");
            }
        }
    }

    /** Deadline 3s; timeout/mat ket noi thi thu lai DUNG 1 LAN voi deadline 5s truoc khi bao loi. */
    private com.quickbite.edu.grpc.UserContactResponse getUserContactWithRetry(GetUserContactRequest request) {
        try {
            return identityServiceBlockingStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getUserContact(request);
        } catch (StatusRuntimeException ex) {
            Status.Code code = ex.getStatus().getCode();
            if (code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.UNAVAILABLE) {
                log.warn("gRPC getUserContact {} - thu lai 1 lan voi deadline {}s", code, RETRY_DEADLINE_SECONDS);
                return identityServiceBlockingStub
                        .withDeadlineAfter(RETRY_DEADLINE_SECONDS, TimeUnit.SECONDS)
                        .getUserContact(request);
            }
            throw ex;
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
