package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

/** channel: "WHATSAPP" | "EMAIL". destination overrides the customer's stored phone/email if provided. */
public record SendInvoiceRequest(String channel, String destination) {
}
