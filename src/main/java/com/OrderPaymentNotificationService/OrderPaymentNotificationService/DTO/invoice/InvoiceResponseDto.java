package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponseDto(
        UUID id,
        String invoiceNumber,
        String status,
        CustomerRequest customer,
        List<InvoiceItemResponseDto> items,
        double subtotalRupees,
        double discountRupees,
        double taxRupees,
        double totalRupees,
        String currency,
        Instant issuedAt,
        Instant dueAt,
        Instant createdAt
) {
}
