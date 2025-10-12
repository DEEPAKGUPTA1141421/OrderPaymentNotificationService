package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.KafkaProducerService;

import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/kafka")
@Lazy
public class KafkaController {

    private final KafkaProducerService producerService;

    public KafkaController(KafkaProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/send")
    public String send(@Valid @RequestBody NotificationRequest request) {
        // Prepare Kafka message format: type|to|subject|body
        String msg = String.format("%s|%s|%s|%s",
                request.getType(),
                request.getTo(),
                request.getSubject(),
                request.getBody());

        producerService.sendMessage("notification", msg);

        return "Message sent: " + msg;
    }
}
