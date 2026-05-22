package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.delivery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {
    private UUID bookingId;
    private UUID customerId;       // booking.userId — the customer who placed the order
    private UUID shopId;           // seller shop (used as origin reference)
    private UUID deliveryAddressId; // delivery address UUID (destination reference)
    private String totalAmountPaise;
    private Instant confirmedAt;
}
