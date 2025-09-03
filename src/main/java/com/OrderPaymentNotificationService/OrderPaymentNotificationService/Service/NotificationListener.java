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

    @KafkaListener(topics = "notification", groupId = "spring-group")
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



//eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiI5NjA4NTU3MDk1Iiwicm9sZSI6IlNFTExFUiIsImlkIjoiYjFiYzRkYmQtYTE5ZC00ZjE2LWExMzctYjQwYzlmMWQxZWU3IiwiaWF0IjoxNzU2MTA0MzMxLCJleHAiOjE3NTc0MDAzMzF9.Fwnj6buNlqQKHDtfISi1GGHG2DHsfnCaTE-UPVopXZSsjOWjNBBJoUGzQ5bK0gOqU2udfp_4W7vVNK9wU941ASPdU2j3A6DfTgJldYg-RPBxMU-VPEA9HHVl4eyBcUtQZTI2Lz6ogXO-QXQO2GR110OVtPIQ73C53d4I9FYcbdXlPH4mmDA7TazXfCrEcaB7VzRKWEgwoEIi8rCyIi2bbSE6NSipcXRtjPWPSP7fU7L20GkaEr4R5mupuvzOjYdWKUod0eLTqzMLamcFyb6oAsp7U7izIeGcyjlPJ-Yk3GrPlg8I1SRs9_X6IwPpkBymDlvc4sYNku0LTGVq3ogEwQ