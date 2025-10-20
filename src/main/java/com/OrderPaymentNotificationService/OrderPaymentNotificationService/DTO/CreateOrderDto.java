package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateOrderDto(

                @NotBlank(message = "Gateway name is required") @Pattern(regexp = "^(phonepe|razorpay|googlepay)$", message = "Gateway must be one of: phonepe, razorpay, googlepay") String gateway,

                @NotNull(message = "Booking ID is required") UUID bookingId,

                @NotNull(message = "User ID is required") UUID userId,

                @NotBlank(message = "Idempotency key is required") @Size(min = 8, max = 64, message = "Idempotency key must be between 8 and 64 characters") String idempotencyKey,

                @NotBlank(message = "PG Payment amount is required") @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Invalid amount format") String pgPaymentAmount,

                @NotNull(message = "pgPayment flag must be provided") Boolean  pgPayment,

                @NotNull(message = "pointPayment flag must be provided") Boolean pointPayment,

                @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Invalid point payment amount format") String pointPaymentAmount) {
}
