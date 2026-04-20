package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Configuration;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.OrderLifecycleEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ranking.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ChatKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private static final String GROUP_ID = "chat-lifecycle-group";

    // ── OrderPlaced (reuses orders.events topic with a separate consumer group) ──

    @Bean
    public ConsumerFactory<String, OrderPlacedEvent> chatOrderPlacedConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        JsonDeserializer<OrderPlacedEvent> deserializer = new JsonDeserializer<>(OrderPlacedEvent.class);
        deserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderPlacedEvent> chatOrderPlacedContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderPlacedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(chatOrderPlacedConsumerFactory());
        return factory;
    }

    // ── RiderAssigned + OrderDelivered (dedicated lifecycle topics) ──────────────

    @Bean
    public ConsumerFactory<String, OrderLifecycleEvent> chatLifecycleConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        JsonDeserializer<OrderLifecycleEvent> deserializer = new JsonDeserializer<>(OrderLifecycleEvent.class);
        deserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderLifecycleEvent> chatLifecycleContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderLifecycleEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(chatLifecycleConsumerFactory());
        return factory;
    }
}
