package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

import java.util.UUID;

public record InvoiceItemResponseDto(
        UUID id,
        String itemType,
        UUID productId,
        UUID variantId,
        String name,
        String sku,
        String barcode,
        Double catalogPriceRupees,
        double unitPriceRupees,
        int quantity,
        double discountRupees,
        double taxRate,
        double taxRupees,
        double totalRupees,
        boolean priceOverride,
        String overrideReason
) {
}
