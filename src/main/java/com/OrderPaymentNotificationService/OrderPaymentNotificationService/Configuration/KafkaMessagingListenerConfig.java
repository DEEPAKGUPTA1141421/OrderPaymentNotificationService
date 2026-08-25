package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Configuration;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.NotificationEventListener;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.NotificationListener;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptConsumerService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptProducerService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat.ChatLifecycleConsumer;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking.OrderEventListener;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Wires each topic to its handler(s) as a raw (non-@KafkaListener) container,
 * so no consumer starts — and no connection to a broker is attempted — unless
 * app.messaging.provider=kafka. Mirrors RedisMessagingListenerConfig's topic
 * table; keep the two in sync when adding a new event.
 *
 * Ack policy: the offset is committed only after the handler returns without
 * throwing. A handler that swallows its own errors (the common case here)
 * therefore always acks; ReceiptConsumerService.handleReceiptEvent
 * deliberately lets failures propagate so its message is retried instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging", name = "provider", havingValue = "kafka", matchIfMissing = true)
public class KafkaMessagingListenerConfig {

    @Value("${chat.lifecycle.order-placed.topic:orders.events}")
    private String orderPlacedTopic;

    @Value("${chat.lifecycle.rider-assigned.topic:rider.assigned.events}")
    private String riderAssignedTopic;

    @Value("${chat.lifecycle.order-delivered.topic:order.delivered.events}")
    private String orderDeliveredTopic;

    private final ConsumerFactory<String, String> consumerFactory;
    private final NotificationListener notificationListener;
    private final NotificationEventListener notificationEventListener;
    private final ChatLifecycleConsumer chatLifecycleConsumer;
    private final OrderEventListener orderEventListener;
    private final ReceiptConsumerService receiptConsumerService;

    @PostConstruct
    public void startListeners() {
        listen("notification", "spring-group", notificationListener::handleNotification);
        listen("notification.events", "notification-service-group", notificationEventListener::handleNotificationEvent);
        listen(orderPlacedTopic, "chat-lifecycle-group", chatLifecycleConsumer::handleOrderPlaced);
        listen("orders.events", "orderservice-group", orderEventListener::handle);
        listen(riderAssignedTopic, "chat-lifecycle-group", chatLifecycleConsumer::handleRiderAssigned);
        listen(orderDeliveredTopic, "chat-lifecycle-group", chatLifecycleConsumer::handleOrderDelivered);
        listen(ReceiptProducerService.TOPIC, "receipt-generator-group", receiptConsumerService::handleReceiptEvent);
        log.info("Kafka messaging listeners started (app.messaging.provider=kafka)");
    }

    private void listen(String topic, String groupId, Consumer<String> handler) {
        ContainerProperties containerProps = new ContainerProperties(topic);
        containerProps.setGroupId(groupId);
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProps.setMessageListener((AcknowledgingMessageListener<String, String>) (record, ack) -> {
            try {
                handler.accept(record.value());
                if (ack != null) ack.acknowledge();
            } catch (Exception e) {
                log.error("Listener failed for topic={} groupId={} — not acking: {}", topic, groupId, e.getMessage(), e);
            }
        });

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
        container.setBeanName(topic + "." + groupId);
        container.setConcurrency(1);
        container.start();
    }
}
