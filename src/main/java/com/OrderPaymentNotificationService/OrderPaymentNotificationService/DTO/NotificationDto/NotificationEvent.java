package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka event published by any microservice (OrderService, ProductService,
 * etc.)
 * to trigger cross-service notifications without direct coupling.
 *
 * Topic: "notification.events"
 * Schema: JSON (serialized via Jackson)
 *
 * Usage (from any microservice via KafkaTemplate):
 * 
 * <pre>
 * NotificationEvent event = NotificationEvent.builder()
 *         .userId(userId)
 *         .category("ORDER_UPDATES")
 *         .title("Order Shipped!")
 *         .body("Your order #ORD-1234 is on the way.")
 *         .actionUrl("/orders/" + orderId)
 *         .referenceId(orderId.toString())
 *         .build();
 * kafkaTemplate.send("notification.events", userId.toString(), event);
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /** Target user — routing key in Kafka for ordered delivery per user. */
    private UUID userId;

    /**
     * Notification category — must match
     * NotificationPreference.NotificationCategory enum.
     * Sent as String for cross-service compatibility (no shared enum dependency).
     */
    private String category;

    /** Short notification title (max ~65 chars for push). */
    private String title;

    /** Full notification body. */
    private String body;

    /**
     * Deep-link URL for tap action in app / email CTA button.
     * e.g. "/orders/uuid", "/wallet", "/loyalty"
     */
    private String actionUrl;

    /**
     * Reference entity ID (orderId, paymentId, etc.)
     * Used for correlation and deduplication.
     */
    private String referenceId;

    /**
     * Optional extra data for push notification data payload.
     * e.g. { "trackingUrl": "...", "estimatedDelivery": "..." }
     */
    private Map<String, String> pushData;

    /** Publishing service name — for traceability (e.g. "order-service"). */
    private String sourceService;

    /** Event creation time — for ordering and deduplication checks. */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Idempotency key — prevents duplicate notifications on Kafka redelivery. */
    private String idempotencyKey;
}