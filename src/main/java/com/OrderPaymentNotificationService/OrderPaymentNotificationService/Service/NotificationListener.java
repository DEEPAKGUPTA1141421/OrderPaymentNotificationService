package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationRequest;

/**
 * Handles the legacy "notification" topic, delivered via Kafka or Redis
 * depending on app.messaging.provider (see KafkaMessagingListenerConfig /
 * RedisMessagingListenerConfig for the wiring).
 */
@Service
@Slf4j
public class NotificationListener {

    private final NotificationFactory factory;
    private final ObjectMapper objectMapper;

    public NotificationListener(NotificationFactory factory, ObjectMapper objectMapper) {
        this.factory = factory;
        this.objectMapper = objectMapper;
    }

    public void handleNotification(String payload) {
        NotificationRequest notification;
        try {
            notification = objectMapper.readValue(payload, NotificationRequest.class);
        } catch (Exception e) {
            log.warn("Failed to parse notification payload: {}", e.getMessage());
            return;
        }

        String type = notification.getType();
        String to = notification.getTo();
        String subject = notification.getSubject();
        String body = notification.getBody();

        NotificationService service = factory.getService(type + "NotificationService");
        if (service == null) {
            log.warn("notification type not available: {}", type);
            return;
        }
        service.sendNotification(to, subject, body, null);
    }
}