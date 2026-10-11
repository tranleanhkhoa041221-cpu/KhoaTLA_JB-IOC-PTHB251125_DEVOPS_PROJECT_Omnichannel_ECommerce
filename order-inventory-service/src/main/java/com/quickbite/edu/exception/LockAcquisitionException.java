package com.quickbite.edu.exception;

/**
 * Nem ra khi khong lay duoc Redis Distributed Lock trong thoi gian cho phep,
 * thuong xay ra khi co qua nhieu request cung tranh chap 1 SKU tai 1 thoi diem (Flash Sale).
 */
public class LockAcquisitionException extends ConflictException {
    public LockAcquisitionException(String sku) {
        super("He thong dang xu ly luong truy cap lon cho san pham SKU [" + sku + "], vui long thu lai");
    }
}
