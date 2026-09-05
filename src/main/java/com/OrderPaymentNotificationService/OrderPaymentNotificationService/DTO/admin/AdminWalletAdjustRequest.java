package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.admin;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for admin wallet credit/debit.
 * Amount is expressed in rupees (converted to paise server-side, matching
 * the convention used by {@code AddMoneyRequestDto}).
 */
public record AdminWalletAdjustRequest(

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "reason is required")
        String reason) {
}
