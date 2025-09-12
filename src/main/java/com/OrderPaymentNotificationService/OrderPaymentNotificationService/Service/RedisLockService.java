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
    private static final String LOCK_PREFIX = "lock:product:";

    public boolean acquireLock(UUID userId, UUID productId, int quantity, long ttlMinutes) {
        String key = buildKey(userId, productId);

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(quantity), Duration.ofMinutes(ttlMinutes));

        return Boolean.TRUE.equals(success);
    }

    public void releaseLock(UUID userId, UUID productId) {
        String key = buildKey(userId, productId);
        redisTemplate.delete(key);
    }

    public Integer getLockedQuantity(UUID userId, UUID productId) {
        String key = buildKey(userId, productId);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value) : null;
    }

    public boolean isLocked(UUID userId, UUID productId) {
        String key = buildKey(userId, productId);
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

    private String buildKey(UUID userId, UUID productId) {
        return LOCK_PREFIX + productId + ":" + userId;
    }
}
