package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.OrderDto;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full projection used by the order-detail page.
 * Includes booking metadata, all line-items, and complete payment breakdown.
 */
public record OrderDetailDto(
        UUID    bookingId,
        UUID    shopId,
        UUID    deliveryAddress,
        String  status,
        String  statusLabel,
        String  totalAmountPaise,
        String  totalAmountRupees,
        Instant expiresAt,
        Instant createdAt,
        List<ItemDto>  items,
        PaymentDto     payment          // null when no payment record exists yet
) {

    // ── Line-item ─────────────────────────────────────────────────────────────

    /**
     * One product/variant line in the booking.
     *
     * unitPricePaise  — price per single unit in paise
     * lineTotalPaise  — unitPricePaise × quantity
     * (Rupee strings pre-converted for convenience)
     */
    public record ItemDto(
            UUID   bookingItemId,
            UUID   productId,
            UUID   variantId,
            int    quantity,
            String unitPricePaise,
            String unitPriceRupees,
            String lineTotalPaise,
            String lineTotalRupees
    ) {}

    // ── Payment ───────────────────────────────────────────────────────────────

    /**
     * Top-level payment record for the booking.
     *
     * paidAmountPaise — how much has actually been collected so far
     * transactions    — one entry per payment leg (GATEWAY, POINTS, COD)
     */
    public record PaymentDto(
            UUID   paymentId,
            String status,
            String totalAmountPaise,
            String totalAmountRupees,
            String paidAmountPaise,
            List<TransactionDto> transactions
    ) {}

    // ── Transaction ───────────────────────────────────────────────────────────

    /**
     * Individual payment attempt / leg.
     *
     * method    — "COD" | "GATEWAY" | "POINTS"
     * orderId   — PhonePe's merchant order ID; null for COD/POINTS legs
     * createdAt — when the transaction was first created (IST)
     * updatedAt — last status change timestamp (IST)
     */
    public record TransactionDto(
            UUID          transactionId,
            String        method,
            String        status,
            String        amountPaise,
            String        amountRupees,
            String        orderId,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {}
}
