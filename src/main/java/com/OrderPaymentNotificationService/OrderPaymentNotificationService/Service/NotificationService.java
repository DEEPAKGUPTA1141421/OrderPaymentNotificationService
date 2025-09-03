package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;
import java.io.File;

public interface NotificationService {
    void sendNotification(String to, String subject, String message, File attachment);
}
