package com.quickbite.edu.exception;

/** 403 - token hop le nhung khong co quyen truy cap tai nguyen (vd: xem don hang cua nguoi khac). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
