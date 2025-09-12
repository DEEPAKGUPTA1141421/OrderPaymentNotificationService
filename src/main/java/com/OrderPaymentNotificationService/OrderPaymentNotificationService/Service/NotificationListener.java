package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationRequest;

@Service
public class NotificationListener {

    private final NotificationFactory factory;

    public NotificationListener(NotificationFactory factory) {
        this.factory = factory;
    }

    @KafkaListener(topics = "notification", groupId = "spring-group", autoStartup = "false")
    public void listen(NotificationRequest notification) {
        String type = notification.getType();
        String to = notification.getTo();
        String subject = notification.getSubject();
        String body = notification.getBody();

        NotificationService service = factory.getService(type + "NotificationService");
        if (service == null) {
            System.out.println("notification type not available");
            return;
        }
        service.sendNotification(to, subject, body, null);
    }
}

// jik njyggtfrfttg huuuiuuujh