package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.admin;

import jakarta.validation.constraints.NotBlank;

/** Request body for an admin-forced order status change. */
public record AdminOrderStatusRequest(

        @NotBlank(message = "status is required")
        String status,

        /** Optional audit note explaining why the status was forced. */
        String reason) {
}
