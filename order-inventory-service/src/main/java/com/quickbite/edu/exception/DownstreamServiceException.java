package com.quickbite.edu.exception;

import org.springframework.http.HttpStatus;

/**
 * Nem ra khi MOT DICH VU PHIA SAU (Identity/Product qua gRPC) cham hoac khong ket noi duoc.
 * Day la loi phia SERVER (5xx), KHONG phai loi cua client:
 *  - 504 GATEWAY_TIMEOUT: dich vu phia sau tra loi qua cham (gRPC DEADLINE_EXCEEDED)
 *  - 503 SERVICE_UNAVAILABLE: khong ket noi duoc / dang qua tai (gRPC UNAVAILABLE va cac loi con lai)
 * Truoc day cac truong hop nay bi tra nham la 400 Bad Request (client tuong minh gui sai du lieu).
 */
public class DownstreamServiceException extends RuntimeException {

    private final HttpStatus status;

    public DownstreamServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
