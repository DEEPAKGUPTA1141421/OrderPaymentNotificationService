package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;


import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class NotificationFactory {
    private final Map<String, NotificationService> services;

    public NotificationFactory(Map<String, NotificationService> services) {
        this.services = services;
    }

    public NotificationService getService(String type) {
        return services.get(type); // "smsNotificationService" or "emailNotificationService"
    }
}
