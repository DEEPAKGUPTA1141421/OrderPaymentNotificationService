package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis pub/sub is fire-and-forget: no consumer groups, no offsets, no replay.
 * A subscriber that isn't running when a message is published simply misses it.
 * Fine for single-instance / dev hosting; switch back to Kafka before scaling
 * to multiple instances of a consumer that needs exactly-once delivery.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "provider", havingValue = "redis")
public class RedisEventPublisher implements EventPublisher {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void publish(String topic, String payload) {
        redisTemplate.convertAndSend(topic, payload);
    }
}
