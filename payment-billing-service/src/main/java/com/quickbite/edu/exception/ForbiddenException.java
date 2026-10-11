package com.quickbite.edu.exception;

/** 403 - token hop le nhung khong co quyen truy cap giao dich thanh toan cua nguoi khac. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
