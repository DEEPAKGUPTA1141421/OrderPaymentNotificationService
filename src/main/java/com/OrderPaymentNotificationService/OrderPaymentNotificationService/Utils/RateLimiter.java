package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final int MAX_REQUESTS_PER_MIN = 5;

    public boolean allow(String key) {
        String redisKey = "rate_limit:" + key;
        Long requests = redisTemplate.opsForValue().increment(redisKey);
        if (requests == 1) {
            redisTemplate.expire(redisKey, Duration.ofMinutes(1));
        }
        return requests <= MAX_REQUESTS_PER_MIN;
    }
}
