package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.receipt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event payload published to {@code order.receipt.generate} when a
 * booking is confirmed.  The receipt consumer deserialises this and generates
 * a GST-compliant PDF invoice.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReceiptEvent {

    private UUID    bookingId;
    private UUID    paymentId;       // null when no payment record yet (COD edge case)
    private UUID    userId;
    private UUID    shopId;
    private String  paymentMode;     // COD | ONLINE | POINTS | MIXED
    private String  totalAmountPaise;
    private String  paidAmountPaise;
    private Instant orderConfirmedAt;
    private List<ReceiptItemEvent> items;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReceiptItemEvent {
        private UUID   productId;
        private UUID   variantId;
        private int    quantity;
        private String unitPricePaise;  // per-unit price in paise
        private String lineTotalPaise;  // unitPricePaise × quantity
    }
}
