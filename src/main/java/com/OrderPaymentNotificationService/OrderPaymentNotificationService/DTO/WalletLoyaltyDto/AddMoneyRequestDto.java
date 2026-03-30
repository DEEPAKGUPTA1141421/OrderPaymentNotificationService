package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Getter;

@Getter
public class AddMoneyRequestDto {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum top-up is ₹1")
    @DecimalMax(value = "100000.00", message = "Maximum top-up per transaction is ₹1,00,000")
    @Digits(integer = 6, fraction = 2, message = "Invalid amount format")
    private BigDecimal amountRupees;

    /**
     * Payment method type: UPI / CARD / NET_BANKING.
     */
    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(UPI|CARD|NET_BANKING)$", message = "paymentMethod must be UPI, CARD, or NET_BANKING")
    private String paymentMethod;

    /**
     * For saved-card payments, pass the saved payment method ID.
     * For UPI, pass the UPI ID string.
     * For new cards, use the gateway's tokenized card data —
     * never raw card numbers through our API.
     */
    private UUID savedPaymentMethodId; // optional: if using saved card

    private String upiId; // optional: direct UPI

    /**
     * Idempotency key supplied by client to prevent double top-ups
     * (re-send safe: same key → same result).
     */
    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 100, message = "idempotencyKey max 100 chars")
    private String idempotencyKey;

    @NotBlank(message = "Gateway name is required")
    @Pattern(regexp = "^(phonepe|razorpay|googlepay)$", message = "Gateway must be one of: phonepe, razorpay, googlepay")
    String gateway;
}
