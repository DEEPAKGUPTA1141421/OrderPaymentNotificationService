package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.OrderLifecycleEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ranking.OrderPlacedEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Handles order-lifecycle events for SendBird chat channel management.
 * Delivered via Kafka or Redis depending on app.messaging.provider (see
 * KafkaMessagingListenerConfig / RedisMessagingListenerConfig for the
 * topic wiring — topic names are configurable via chat.lifecycle.* props).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatLifecycleConsumer {

    private final ChatChannelService chatChannelService;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Handles the orders.events topic (same topic as the ranking service's
     * OrderEventListener, but as an independent subscriber so both receive
     * every event).
     *
     * Looks up the Booking to get customerId, since OrderPlacedEvent only
     * carries orderId + sellerId.
     */
    public void handleOrderPlaced(String payload) {
        OrderPlacedEvent event;
        try {
            event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        } catch (Exception e) {
            log.warn("[ChatLifecycleConsumer] Failed to parse OrderPlaced payload: {}", e.getMessage());
            return;
        }
        log.info("[ChatLifecycleConsumer] OrderPlaced: orderId={}", event.getOrderId());
        try {
            bookingRepository.findById(event.getOrderId()).ifPresentOrElse(
                booking -> chatChannelService.createCustomerSellerChannel(
                    event.getOrderId(), booking.getUserId(), event.getSellerId()),
                () -> log.warn("[ChatLifecycleConsumer] Booking not found for orderId={}", event.getOrderId())
            );
        } catch (Exception e) {
            log.error("[ChatLifecycleConsumer] Failed to create customer-seller channel for order {}: {}",
                event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handles the rider.assigned.events topic (chat.lifecycle.rider-assigned.topic).
     * OrderLifecycleEvent must carry orderId + customerId + riderId.
     */
    public void handleRiderAssigned(String payload) {
        OrderLifecycleEvent event;
        try {
            event = objectMapper.readValue(payload, OrderLifecycleEvent.class);
        } catch (Exception e) {
            log.warn("[ChatLifecycleConsumer] Failed to parse RiderAssigned payload: {}", e.getMessage());
            return;
        }
        log.info("[ChatLifecycleConsumer] RiderAssigned: orderId={}, riderId={}", event.getOrderId(), event.getRiderId());
        if (event.getOrderId() == null || event.getCustomerId() == null || event.getRiderId() == null) {
            log.warn("[ChatLifecycleConsumer] Dropping malformed RiderAssigned event: {}", event);
            return;
        }
        try {
            chatChannelService.createCustomerRiderChannel(
                event.getOrderId(), event.getCustomerId(), event.getRiderId());
        } catch (Exception e) {
            log.error("[ChatLifecycleConsumer] Failed to create customer-rider channel for order {}: {}",
                event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handles the order.delivered.events topic (chat.lifecycle.order-delivered.topic).
     * Closes the rider channel immediately; schedules seller channel archival after 7 days.
     */
    public void handleOrderDelivered(String payload) {
        OrderLifecycleEvent event;
        try {
            event = objectMapper.readValue(payload, OrderLifecycleEvent.class);
        } catch (Exception e) {
            log.warn("[ChatLifecycleConsumer] Failed to parse OrderDelivered payload: {}", e.getMessage());
            return;
        }
        log.info("[ChatLifecycleConsumer] OrderDelivered: orderId={}", event.getOrderId());
        if (event.getOrderId() == null) {
            log.warn("[ChatLifecycleConsumer] Dropping malformed OrderDelivered event: {}", event);
            return;
        }
        try {
            chatChannelService.handleOrderDelivered(event.getOrderId());
        } catch (Exception e) {
            log.error("[ChatLifecycleConsumer] Failed to handle OrderDelivered for {}: {}",
                event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Runs at the interval defined by sendbird.channel.archival-check-interval-ms (default 1 hour).
     * Archives any customer-seller channels whose archiveScheduledAt has passed.
     */
    @Scheduled(fixedDelayString = "${sendbird.channel.archival-check-interval-ms:3600000}")
    public void archiveScheduledChannels() {
        log.debug("[ChatLifecycleConsumer] Running scheduled channel archival check");
        try {
            chatChannelService.runScheduledArchival();
        } catch (Exception e) {
            log.error("[ChatLifecycleConsumer] Error during scheduled archival: {}", e.getMessage(), e);
        }
    }
}
