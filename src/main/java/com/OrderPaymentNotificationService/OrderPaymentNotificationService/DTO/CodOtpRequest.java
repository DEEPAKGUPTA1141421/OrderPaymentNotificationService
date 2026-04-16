package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for COD OTP generation.
 * The user calls this before the delivery partner arrives so the OTP is ready.
 */
public record CodOtpRequest(

        @NotNull(message = "Transaction ID is required")
        UUID transactionId) {
}
