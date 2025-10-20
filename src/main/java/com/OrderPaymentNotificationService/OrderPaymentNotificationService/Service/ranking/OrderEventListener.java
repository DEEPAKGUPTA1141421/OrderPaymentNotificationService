package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ranking.OrderPlacedEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.ShopStats;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ShopStatsRepository;

import java.util.UUID;

@Service
public class OrderEventListener {

    private final ShopStatsRepository shopStatsRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public OrderEventListener(ShopStatsRepository shopStatsRepository,
            RedisTemplate<String, Object> redisTemplate) {
        this.shopStatsRepository = shopStatsRepository;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = "orders.events", groupId = "orderservice-group")
    @Transactional
    public void handle(OrderPlacedEvent event) {
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
