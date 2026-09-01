package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

import java.time.Instant;
import java.util.UUID;

public record InvoiceSummaryDto(
        UUID id,
        String invoiceNumber,
        String status,
        String customerName,
        double totalRupees,
        String lastDeliveryChannel,   // "WHATSAPP" | "EMAIL" | null
        Instant createdAt
) {
}
