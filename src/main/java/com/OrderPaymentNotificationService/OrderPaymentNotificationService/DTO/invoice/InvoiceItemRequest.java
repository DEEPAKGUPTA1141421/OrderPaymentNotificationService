package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

import java.util.UUID;

/**
 * One line item on a create/update invoice request.
 * type=CATALOG requires productId/variantId; type=CUSTOM requires name (and
 * optionally sku/barcode) since it has no backing catalog product.
 */
public record InvoiceItemRequest(
        String type,              // "CATALOG" | "CUSTOM"

        UUID productId,           // CATALOG only
        UUID variantId,           // CATALOG only

        String name,              // CUSTOM only (CATALOG name is resolved server-side)
        String sku,               // CUSTOM only
        String barcode,           // CUSTOM only

        double unitPrice,         // rupees — the seller's chosen invoice price
        int quantity,
        double discount,          // rupees, line-level
        Double taxRate,           // percent, e.g. 18.0 — defaults to the product's GST rate for CATALOG, 0 for CUSTOM if omitted

        // CATALOG only, optional: when variantId is known the backend re-fetches the
        // authoritative catalog price/GST via the internal product service and ignores
        // this; when the seller's product has no resolvable variant, this client-supplied
        // hint (read from the same product-search result used to add the item) is used
        // for price-deviation validation instead, since there is nothing to look up.
        Double catalogPriceHint,

        boolean priceOverrideConfirmed, // must be true if this price trips the deviation warning
        String overrideReason
) {
}
