package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ranking.OrderPlacedEvent;

@Service
public class OrderProducer {
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private static final String TOPIC = "orders.events";

    public OrderProducer(KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(OrderPlacedEvent event) {
        kafkaTemplate.send(TOPIC, event.getSellerId().toString(), event);
    }
}