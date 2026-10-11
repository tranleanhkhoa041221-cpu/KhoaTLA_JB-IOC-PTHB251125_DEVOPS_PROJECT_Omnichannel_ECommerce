package com.quickbite.edu.lock;

import com.quickbite.edu.exception.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis Distributed Lock (don gian hoa cua Redlock) dung de bao ve vung tranh chap
 * (critical section) khi nhieu instance/thread cung dat mua 1 SKU tai cung 1 thoi
 * diem - giai quyet bai toan "100 nguoi cung mua 1 san pham con ton kho = 1".
 *
 * Co che:
 *  - Lock:   SET lock:inventory:{sku} {token} NX PX {ttl}
 *  - Unlock: script Lua so sanh token roi moi DEL (tranh xoa nham lock cua tien trinh khac)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private static final String LOCK_PREFIX = "lock:inventory:";
    private static final long DEFAULT_TTL_MS = 5_000L;
    private static final long WAIT_TIMEOUT_MS = 3_000L;
    private static final long RETRY_DELAY_MS = 50L;

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private final StringRedisTemplate redisTemplate;

    private String tryLock(String sku, long ttlMs) {
        String key = LOCK_PREFIX + sku;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, token, Duration.ofMillis(ttlMs));
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    private void unlock(String sku, String token) {
        if (token == null) {
            return;
        }
        String key = LOCK_PREFIX + sku;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        redisTemplate.execute(script, Collections.singletonList(key), token);
    }

    /**
     * Chay {@code action} bon trong 1 Redis Distributed Lock ung voi tung SKU.
     * Se retry (poll) toi da {@code WAIT_TIMEOUT_MS} de lay lock truoc khi bao loi.
     */
    public <T> T executeWithLock(String sku, Supplier<T> action) {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        String token;
        while ((token = tryLock(sku, DEFAULT_TTL_MS)) == null) {
            if (System.currentTimeMillis() > deadline) {
                throw new LockAcquisitionException(sku);
            }
            sleepQuietly(RETRY_DELAY_MS);
        }
        try {
            return action.get();
        } finally {
            unlock(sku, token);
        }
    }

    /**
     * Khoa nhieu SKU cung luc (dung cho 1 don hang co nhieu san pham).
     * SKU luon duoc sap xep alphabet TRUOC khi khoa tuan tu de tranh Deadlock
     * giua cac request khoa cheo nhau (VD request A khoa SKU1->SKU2,
     * request B khoa SKU2->SKU1 se gay deadlock neu khong sap xep thu tu co dinh).
     */
    public <T> T executeWithLocks(List<String> skus, Supplier<T> action) {
        List<String> sorted = skus.stream().distinct().sorted().toList();
        return lockRecursive(sorted, 0, action);
    }

    private <T> T lockRecursive(List<String> skus, int index, Supplier<T> action) {
        if (index == skus.size()) {
            return action.get();
        }
        return executeWithLock(skus.get(index), () -> lockRecursive(skus, index + 1, action));
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
