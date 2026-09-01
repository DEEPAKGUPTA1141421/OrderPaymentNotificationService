package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.PriceValidationResult;

/**
 * Thrown when a line's entered price deviates enough from the catalog price
 * to need an explicit seller confirmation (priceOverrideConfirmed=true) that
 * wasn't present on the request. Mapped to HTTP 422 by InvoiceController.
 */
public class PriceOverrideRequiredException extends RuntimeException {
    private final int itemIndex;
    private final PriceValidationResult result;

    public PriceOverrideRequiredException(int itemIndex, PriceValidationResult result) {
        super("Price override confirmation required for item " + itemIndex);
        this.itemIndex = itemIndex;
        this.result = result;
    }

    public int getItemIndex() { return itemIndex; }
    public PriceValidationResult getResult() { return result; }
}
