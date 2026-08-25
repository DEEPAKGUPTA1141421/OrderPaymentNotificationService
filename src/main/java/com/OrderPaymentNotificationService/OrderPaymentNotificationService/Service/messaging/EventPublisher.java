package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.messaging;

/**
 * Provider-agnostic pub/sub publisher. Backed by Kafka or Redis depending on
 * app.messaging.provider — see KafkaEventPublisher / RedisEventPublisher.
 */
public interface EventPublisher {

    void publish(String topic, String payload);

    /**
     * Publish with a partition/routing key. Only Kafka uses the key
     * (partition assignment); other providers fall back to publish(topic, payload).
     */
    default void publish(String topic, String key, String payload) {
        publish(topic, payload);
    }
}
