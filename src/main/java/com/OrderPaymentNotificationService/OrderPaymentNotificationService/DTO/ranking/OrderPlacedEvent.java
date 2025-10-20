package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ranking;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;
import java.time.Instant;

@Data
@AllArgsConstructor
public class OrderPlacedEvent {
    private UUID orderId;
    private UUID sellerId;
    private String city;
    private String category; // use string to keep it simple; map to enum if needed
    private Instant createdAt;
}