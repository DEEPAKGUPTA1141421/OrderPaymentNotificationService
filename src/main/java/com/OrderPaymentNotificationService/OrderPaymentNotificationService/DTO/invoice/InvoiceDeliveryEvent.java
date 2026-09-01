package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event payload published to {@code invoice.delivery.requested} when a
 * seller taps "Send" on a finalized invoice. Decouples PDF generation +
 * WhatsApp/Email dispatch from the request path — same pattern as ReceiptEvent.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceDeliveryEvent {
    private UUID invoiceId;
    private UUID deliveryId;   // the InvoiceDelivery row to update on completion
    private String channel;    // WHATSAPP | EMAIL
    private String destination;
}
