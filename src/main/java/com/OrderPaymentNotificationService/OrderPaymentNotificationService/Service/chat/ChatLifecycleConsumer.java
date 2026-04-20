package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.OrderLifecycleEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ranking.OrderPlacedEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatLifecycleConsumer {

    private final ChatChannelService chatChannelService;
    private final BookingRepository bookingRepository;

    /**
     * Listens to the orders.events topic (same topic as the ranking service,
     * but using the separate chat-lifecycle-group consumer group so both
     * consumers receive every event independently).
     *
     * Looks up the Booking to get customerId, since OrderPlacedEvent only
     * carries orderId + sellerId.
     */
    @KafkaListener(
        topics         = "${chat.lifecycle.order-placed.topic:orders.events}",
        groupId        = "chat-lifecycle-group",
        containerFactory = "chatOrderPlacedContainerFactory"
    )
    public void onOrderPlaced(OrderPlacedEvent event) {
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
     * Listens to rider.assigned.events topic (configure chat.lifecycle.rider-assigned.topic).
     * OrderLifecycleEvent must carry orderId + customerId + riderId.
     */
    @KafkaListener(
        topics         = "${chat.lifecycle.rider-assigned.topic:rider.assigned.events}",
        groupId        = "chat-lifecycle-group",
        containerFactory = "chatLifecycleContainerFactory"
    )
    public void onRiderAssigned(OrderLifecycleEvent event) {
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
     * Listens to order.delivered.events topic (configure chat.lifecycle.order-delivered.topic).
     * Closes the rider channel immediately; schedules seller channel archival after 7 days.
     */
    @KafkaListener(
        topics         = "${chat.lifecycle.order-delivered.topic:order.delivered.events}",
        groupId        = "chat-lifecycle-group",
        containerFactory = "chatLifecycleContainerFactory"
    )
    public void onOrderDelivered(OrderLifecycleEvent event) {
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
