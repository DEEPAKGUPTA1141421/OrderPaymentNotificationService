package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Configuration;

// ─── ADD THIS BEAN to your existing KafkaConfig.java ─────────────────────────
//
// This adds a second consumer factory specifically for the notification.events
// topic, which uses NotificationEvent (not the existing NotificationRequest DTO).
//
// 1. Add this import at the top of KafkaConfig.java:
//
//    import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.NotificationEvent;
//    import org.springframework.kafka.listener.ContainerProperties;
//
// 2. Add these two beans inside the KafkaConfig class:

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.NotificationEvent;

@Configuration
public class KafkaConfig_NotificationEventAddition {
    // ↑ This class name is just for reference — paste the beans below into
    // your existing KafkaConfig.java, not into a new class.

    /**
     * Consumer factory for NotificationEvent (cross-service notification events).
     * Uses a dedicated group-id so it doesn't interfere with the existing
     * NotificationRequest consumer.
     */
    @Bean
    public ConsumerFactory<String, NotificationEvent> notificationEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "13.210.73.205:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service-group");

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(NotificationEvent.class, false));
    }

    /**
     * Listener container factory for NotificationEvent.
     * Named "notificationEventListenerContainerFactory" — matches the
     * containerFactory attribute in @KafkaListener in NotificationEventListener.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> notificationEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notificationEventConsumerFactory());

        // Manual acknowledgment mode
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 3 concurrent consumers — match this to your Kafka partition count
        factory.setConcurrency(3);

        return factory;
    }
}