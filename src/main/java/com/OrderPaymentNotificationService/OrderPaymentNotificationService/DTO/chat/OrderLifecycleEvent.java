package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic lifecycle event published by the order service for rider-assigned
 * and order-delivered transitions. Consumed by the chat module to manage
 * SendBird channel lifecycle.
 *
 * Topic names are configurable:
 *   chat.lifecycle.rider-assigned.topic  (default: rider.assigned.events)
 *   chat.lifecycle.order-delivered.topic (default: order.delivered.events)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderLifecycleEvent {
    private UUID orderId;
    private UUID customerId;
    private UUID sellerId;
    private UUID riderId;      // non-null for RiderAssigned; null for OrderDelivered
    private Instant createdAt;
}
