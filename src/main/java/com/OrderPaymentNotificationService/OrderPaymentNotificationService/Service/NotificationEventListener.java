package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.NotificationEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationCategory;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * Kafka consumer for cross-service notification events.
 *
 * Subscribes to "notification.events" topic.
 * Other microservices (OrderService, ProductService) publish NotificationEvent
 * messages here — this service then fans them out to all channels via
 * NotificationDispatcher.
 *
 * Reliability guarantees:
 * - Manual acknowledgment (MANUAL_IMMEDIATE) — no auto-commit.
 * - Idempotency via Redis dedup cache (TTL = 24h).
 * - Invalid category strings are logged and skipped (not re-queued).
 * - Unhandled exceptions are caught and logged; message is still acked
 * to prevent infinite retry on poison-pill messages.
 *
 * Concurrency:
 * - 3 concurrent consumers (concurrency = "3") — tune based on partition count.
 * - Each consumer thread is independent; Redis dedup prevents double-send
 * when multiple consumers receive the same message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationDispatcher dispatcher;
    private final StringRedisTemplate redisTemplate;

    private static final String DEDUP_KEY_PREFIX = "notif_dedup:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    @KafkaListener(topics = "notification.events", groupId = "notification-service-group", containerFactory = "notificationEventListenerContainerFactory", concurrency = "3")
    public void onNotificationEvent(NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("[NotifEventListener] Received: topic={}, partition={}, offset={}, userId={}, category={}",
                topic, partition, offset, event.getUserId(), event.getCategory());

        // 1. Validate required fields
        if (event.getUserId() == null || event.getCategory() == null
                || event.getTitle() == null || event.getBody() == null) {
            log.warn("[NotifEventListener] Dropping malformed event (missing required fields): {}", event);
            return;
        }

        // 2. Idempotency check — skip if already processed (Kafka redelivery safety)
        if (event.getIdempotencyKey() != null && isDuplicate(event.getIdempotencyKey())) {
            log.info("[NotifEventListener] Duplicate idempotencyKey={} — skipping.",
                    event.getIdempotencyKey());
            return;
        }

        // 3. Parse category — invalid categories are logged and skipped
        NotificationCategory category;
        try {
            category = NotificationCategory.valueOf(event.getCategory().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("[NotifEventListener] Unknown category '{}' in event from source='{}'. "
                    + "Valid categories: {}. Dropping event.",
                    event.getCategory(), event.getSourceService(),
                    Arrays.toString(NotificationCategory.values()));
            return;
        }

        // 4. Dispatch to all channels
        try {
            dispatcher.dispatch(
                    event.getUserId(),
                    category,
                    event.getTitle(),
                    event.getBody(),
                    event.getActionUrl(),
                    event.getReferenceId(),
                    event.getPushData() != null ? event.getPushData() : java.util.Map.of());

            // 5. Mark as processed in Redis
            if (event.getIdempotencyKey() != null) {
                markProcessed(event.getIdempotencyKey());
            }

            log.info("[NotifEventListener] Dispatched: userId={}, category={}",
                    event.getUserId(), category);

        } catch (Exception e) {
            // Log but do NOT re-throw — we don't want to retry poison-pill events
            // indefinitely
            log.error("[NotifEventListener] Dispatch failed: userId={}, category={}, error={}",
                    event.getUserId(), category, e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isDuplicate(String idempotencyKey) {
        String redisKey = DEDUP_KEY_PREFIX + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    private void markProcessed(String idempotencyKey) {
        String redisKey = DEDUP_KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, "1", DEDUP_TTL);
    }
}