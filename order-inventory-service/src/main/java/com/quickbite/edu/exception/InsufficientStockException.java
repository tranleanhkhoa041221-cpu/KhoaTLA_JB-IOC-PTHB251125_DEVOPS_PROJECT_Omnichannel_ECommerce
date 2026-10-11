package com.quickbite.edu.exception;

/**
 * Nem ra khi khong du ton kho de giu hang cho don (race condition case:
 * VD 100 nguoi cung mua 1 SKU chi con ton kho = 1).
 */
public class InsufficientStockException extends ConflictException {
    public InsufficientStockException(String sku) {
        super("San pham SKU [" + sku + "] khong du hang trong kho");
    }
}
