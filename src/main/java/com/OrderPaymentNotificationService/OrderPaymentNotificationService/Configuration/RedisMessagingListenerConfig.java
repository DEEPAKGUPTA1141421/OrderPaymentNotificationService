package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Configuration;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.NotificationEventListener;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.NotificationListener;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptConsumerService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptProducerService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat.ChatLifecycleConsumer;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking.OrderEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Subscribes each event handler to a Redis pub/sub channel named after the
 * Kafka topic it replaces. No consumer groups here: every subscriber attached
 * to a channel gets every message — which matches "orders.events" having two
 * independent Kafka consumer groups (OrderEventListener + ChatLifecycleConsumer)
 * today, so nothing changes semantically.
 *
 * Caveats vs. Kafka: no durability/replay (a subscriber that's down misses the
 * message), no partition-key ordering, and ReceiptConsumerService's "don't ack
 * on failure so it gets retried" behavior has no equivalent — a failed receipt
 * generation is just logged and dropped under Redis. Acceptable for
 * single-instance/dev hosting; switch app.messaging.provider back to "kafka"
 * before that matters.
 *
 * Mirrors KafkaMessagingListenerConfig's topic table; keep the two in sync
 * when adding a new event.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging", name = "provider", havingValue = "redis")
public class RedisMessagingListenerConfig {

    @Value("${chat.lifecycle.order-placed.topic:orders.events}")
    private String orderPlacedTopic;

    @Value("${chat.lifecycle.rider-assigned.topic:rider.assigned.events}")
    private String riderAssignedTopic;

    @Value("${chat.lifecycle.order-delivered.topic:order.delivered.events}")
    private String orderDeliveredTopic;

    private final NotificationListener notificationListener;
    private final NotificationEventListener notificationEventListener;
    private final ChatLifecycleConsumer chatLifecycleConsumer;
    private final OrderEventListener orderEventListener;
    private final ReceiptConsumerService receiptConsumerService;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        subscribe(container, "notification", notificationListener::handleNotification);
        subscribe(container, "notification.events", notificationEventListener::handleNotificationEvent);
        subscribe(container, orderPlacedTopic, chatLifecycleConsumer::handleOrderPlaced);
        subscribe(container, "orders.events", orderEventListener::handle);
        subscribe(container, riderAssignedTopic, chatLifecycleConsumer::handleRiderAssigned);
        subscribe(container, orderDeliveredTopic, chatLifecycleConsumer::handleOrderDelivered);
        subscribe(container, ReceiptProducerService.TOPIC, receiptConsumerService::handleReceiptEvent);

        log.info("Redis pub/sub messaging listeners started (app.messaging.provider=redis)");
        return container;
    }

    private void subscribe(RedisMessageListenerContainer container, String channel, Consumer<String> handler) {
        container.addMessageListener((message, pattern) -> {
            try {
                handler.accept(new String(message.getBody(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.error("Listener failed for channel={}: {}", channel, e.getMessage(), e);
            }
        }, new ChannelTopic(channel));
    }
}
