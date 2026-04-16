package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for the delivery partner to generate a payment QR code.
 * The rider calls this when they arrive at the customer's door and the
 * customer wants to pay digitally instead of with cash.
 */
public record CodQrRequest(

        @NotNull(message = "Transaction ID is required")
        UUID transactionId) {
}
