package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.admin;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for an admin-initiated refund. Delegates to the same
 * {@code PaymentGateway#refundPayment} logic used by the customer-facing
 * refund endpoint — this DTO only carries the extra routing/audit fields
 * an admin needs to supply.
 */
public record AdminRefundRequest(

        @NotBlank(message = "gateway is required")
        String gateway,

        /** Optional — defaults to the payment's first transaction when omitted. */
        UUID transactionId,

        /** Optional — defaults to the payment's totalAmount (paise) when omitted. */
        String amount,

        @NotBlank(message = "reason is required")
        String reason) {
}
