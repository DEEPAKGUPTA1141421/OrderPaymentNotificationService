package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisLockService {
    private final StringRedisTemplate redisTemplate;
    public static final String LOCK_PREFIX = "lock:product:";
    public static final String CART_LOCK_PREFIX = "lock:cart:";

    public boolean acquireLock(String lock_prefix, UUID userId, UUID productId, int quantity, long ttlMinutes) {
        String key = buildKey(lock_prefix, userId, productId);

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(quantity), Duration.ofMinutes(ttlMinutes));

        return Boolean.TRUE.equals(success);
    }

    public void releaseLock(String lock_prefix, UUID userId, UUID productId) {
        String key = buildKey(lock_prefix, userId, productId);
        redisTemplate.delete(key);
    }

    public Integer getLockedQuantity(String lock_prefix, UUID userId, UUID productId) {
        String key = buildKey(lock_prefix, userId, productId);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value) : null;
    }

    public boolean isLocked(String lock_prefix, UUID userId, UUID productId) {
        String key = buildKey(lock_prefix, userId, productId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public int getTotalLockedQuantity(UUID productId) {
        String pattern = LOCK_PREFIX + productId + ":*";
        int total = 0;

        for (String key : redisTemplate.keys(pattern)) {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                total += Integer.parseInt(value);
            }
        }

        return total;
    }

    private String buildKey(String lock_prefix, UUID userId, UUID productId) {
        return lock_prefix + productId + ":" + userId;
    }

    public boolean acquireCartLock(UUID userId, long ttlMinutes) {
        String key = CART_LOCK_PREFIX + userId;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "locked", Duration.ofMinutes(ttlMinutes));
        return Boolean.TRUE.equals(success);
    }

    public void releaseCartLock(UUID userId) {
        String key = CART_LOCK_PREFIX + userId;
        redisTemplate.delete(key);
    }

    public boolean isCartLocked(UUID userId) {
        String key = CART_LOCK_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
