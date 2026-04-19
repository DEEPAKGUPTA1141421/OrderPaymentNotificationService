package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.OrderDto;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection used by the order-list page.
 * One record per booking — no item or transaction details.
 *
 * Fields:
 *  bookingId        — stable identifier; use in deep-link to detail page
 *  shopId           — identifier of the seller
 *  status           — raw enum name (e.g. "CONFIRMED")
 *  statusLabel      — display-friendly label (e.g. "Confirmed")
 *  itemCount        — number of line-items in the booking
 *  totalAmountPaise — grand total stored in paise (divide by 100 for ₹)
 *  totalAmountRupees— pre-converted rupee string (e.g. "499.00")
 *  paymentStatus    — raw Payment.Status name, null when not yet initiated
 *  paymentMode      — "COD" | "ONLINE" | "POINTS" | "MIXED" | "UNPAID"
 *  expiresAt        — booking hold expiry (ISO-8601)
 *  createdAt        — when the booking was created (ISO-8601); null for legacy rows
 */
public record OrderSummaryDto(
        UUID    bookingId,
        UUID    shopId,
        String  status,
        String  statusLabel,
        int     itemCount,
        String  totalAmountPaise,
        String  totalAmountRupees,
        String  paymentStatus,
        String  paymentMode,
        Instant expiresAt,
        Instant createdAt
) {}
// juoii9f uoi88rjirfiiuo iuoi9oriouuguhthurf