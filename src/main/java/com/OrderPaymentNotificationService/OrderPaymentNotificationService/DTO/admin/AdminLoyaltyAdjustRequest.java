package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for admin loyalty point adjustment.
 * {@code points} may be positive (grant) or negative (deduct) — magnitude
 * capped server-side to avoid fat-fingered adjustments.
 */
public record AdminLoyaltyAdjustRequest(

        @NotNull(message = "points is required")
        Long points,

        @NotBlank(message = "reason is required")
        String reason) {
}
