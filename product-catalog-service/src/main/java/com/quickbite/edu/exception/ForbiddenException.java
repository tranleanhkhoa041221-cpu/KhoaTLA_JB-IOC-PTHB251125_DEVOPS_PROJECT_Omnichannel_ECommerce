package com.quickbite.edu.exception;

/**
 * Nguoi goi DA xac thuc (co JWT hop le, da qua API Gateway) nhung KHONG du quyen
 * (role) de thuc hien thao tac - vd CUSTOMER goi API tao/sua/xoa san pham, danh
 * muc, thuong hieu (chi danh cho ADMIN). Khac BadRequestException (sai du lieu)
 * va khac 401 (chua xac thuc) - day la 403 dung ngu nghia HTTP.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
