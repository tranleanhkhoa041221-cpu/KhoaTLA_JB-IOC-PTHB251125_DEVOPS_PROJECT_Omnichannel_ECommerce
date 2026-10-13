package com.quickbite.edu.lock;

import com.quickbite.edu.exception.LockAcquisitionException;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
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
 *  - Lock:   SET lock:inventory:{sku} {token} EX {ttl-giay} NX
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

    /**
     * Tracer cho span Redis TAO THU CONG. OpenTelemetry Java agent (2.32.0) khong tu bat duoc lenh Lettuce 7.5.2
     * cua project nen ta bao tung lenh Redis cua lock bang span CLIENT (db.system=redis) de hien trong Jaeger.
     * Khi chay KHONG co agent (test/local) GlobalOpenTelemetry la no-op, khong anh huong gi.
     */
    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("com.quickbite.edu.redis-lock");

    private final StringRedisTemplate redisTemplate;

    private static Span redisSpan(String operation, String statement) {
        return TRACER.spanBuilder(operation + " lock:inventory")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("db.system", "redis")
                .setAttribute("db.system.name", "redis")
                .setAttribute("db.operation", operation)
                .setAttribute("db.statement", statement)
                .setAttribute("peer.service", "redis")
                .startSpan();
    }

    private String tryLock(String sku, long ttlMs) {
        String key = LOCK_PREFIX + sku;
        String token = UUID.randomUUID().toString();
        Span span = redisSpan("SET", "SET " + key + " ? EX " + (ttlMs / 1000) + " NX");
        try (Scope ignored = span.makeCurrent()) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, Duration.ofMillis(ttlMs));
            span.setAttribute("lock.acquired", Boolean.TRUE.equals(acquired));
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, String.valueOf(e.getMessage()));
            throw e;
        } finally {
            span.end();
        }
    }

    private void unlock(String sku, String token) {
        if (token == null) {
            return;
        }
        String key = LOCK_PREFIX + sku;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        Span span = redisSpan("EVALSHA", "EVALSHA <unlock-lua: get+compare token+del> 1 " + key + " ?");
        try (Scope ignored = span.makeCurrent()) {
            redisTemplate.execute(script, Collections.singletonList(key), token);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, String.valueOf(e.getMessage()));
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Chay {@code action} bon trong 1 Redis Distributed Lock ung voi tung SKU.
     * Se retry (poll) toi da {@code WAIT_TIMEOUT_MS} de lay lock truoc khi bao loi.
     */
    public <T> T executeWithLock(String sku, Supplier<T> action) {
        // Span cha bao tron vung lock (lay lock -> chay action -> tra lock) de thay thoi gian giu lock tren Jaeger.
        Span lockSpan = TRACER.spanBuilder("redis.lock.inventory")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("lock.sku", sku)
                .startSpan();
        try (Scope ignored = lockSpan.makeCurrent()) {
            long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
            String token;
            int retries = 0;
            while ((token = tryLock(sku, DEFAULT_TTL_MS)) == null) {
                if (System.currentTimeMillis() > deadline) {
                    throw new LockAcquisitionException(sku);
                }
                retries++;
                sleepQuietly(RETRY_DELAY_MS);
            }
            lockSpan.setAttribute("lock.retries", retries);
            try {
                return action.get();
            } finally {
                unlock(sku, token);
            }
        } catch (LockAcquisitionException e) {
            // Chi danh dau loi khi KHONG lay duoc lock; loi nghiep vu (het hang...) khong phai loi cua lock.
            lockSpan.recordException(e);
            lockSpan.setStatus(StatusCode.ERROR, "Khong lay duoc lock");
            throw e;
        } finally {
            lockSpan.end();
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