package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.admin;

import jakarta.validation.constraints.NotBlank;

/** Request body for an admin order cancellation. */
public record AdminOrderCancelRequest(

        @NotBlank(message = "reason is required")
        String reason) {
}
