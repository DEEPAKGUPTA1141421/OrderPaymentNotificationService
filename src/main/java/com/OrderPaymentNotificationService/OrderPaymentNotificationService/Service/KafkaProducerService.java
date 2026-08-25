package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.messaging.EventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@Lazy
public class KafkaProducerService {
    private final EventPublisher eventPublisher;

    public KafkaProducerService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void sendMessage(String topic, String message) {
        eventPublisher.publish(topic, message);
    }
}
