package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ranking.OrderPlacedEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.ShopStats;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ShopStatsRepository;

import java.util.UUID;

/**
 * Handles the orders.events topic to maintain shop ranking stats. Delivered
 * via Kafka or Redis depending on app.messaging.provider (see
 * KafkaMessagingListenerConfig / RedisMessagingListenerConfig).
 */
@Service
@Slf4j
public class OrderEventListener {

    private final ShopStatsRepository shopStatsRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventListener(ShopStatsRepository shopStatsRepository,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.shopStatsRepository = shopStatsRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(String payload) {
        OrderPlacedEvent event;
        try {
            event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        } catch (Exception e) {
            log.warn("[OrderEventListener] Failed to parse orders.events payload: {}", e.getMessage());
            return;
        }
        UUID sellerId = event.getSellerId();
        ShopStats stats = shopStatsRepository.findById(sellerId)
                .orElseGet(() -> ShopStats.builder()
                        .sellerId(sellerId)
                        .orderCount(0L)
                        .recentOrders(0L)
                        .avgRating(0.0)
                        .city(event.getCity())
                        .category(event.getCategory())
                        .build());

        stats.setOrderCount(stats.getOrderCount() + 1);
        stats.setRecentOrders(stats.getRecentOrders() + 1); // you can use sliding-window logic later
        shopStatsRepository.save(stats);

        // also update Redis hash for quick retrieval
        String redisKey = "shop:stats:" + sellerId.toString();
        redisTemplate.opsForHash().put(redisKey, "orderCount", stats.getOrderCount());
        redisTemplate.opsForHash().put(redisKey, "recentOrders", stats.getRecentOrders());
        redisTemplate.opsForHash().put(redisKey, "avgRating", stats.getAvgRating());
        redisTemplate.opsForHash().put(redisKey, "city", stats.getCity());
        redisTemplate.opsForHash().put(redisKey, "category", stats.getCategory());
    }
}
