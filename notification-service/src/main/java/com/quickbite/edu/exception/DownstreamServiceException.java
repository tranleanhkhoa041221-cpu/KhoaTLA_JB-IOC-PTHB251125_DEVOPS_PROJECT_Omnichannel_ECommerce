package com.quickbite.edu.exception;

import org.springframework.http.HttpStatus;

/**
 * Loi tu service downstream (Identity...) ma Notification Service khong the xu ly duoc.
 * Se duoc GlobalExceptionHandler map thanh HTTP 502/503/504 tuong ung.
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
