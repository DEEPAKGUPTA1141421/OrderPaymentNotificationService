package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice;

/**
 * Result of comparing a seller-entered invoice price against the catalog price.
 * A warning here is advisory, not a hard block — the caller must resend the
 * item with priceOverrideConfirmed=true (and ideally overrideReason) to proceed.
 */
public record PriceValidationResult(
        boolean warning,
        String reason,                 // e.g. "PRICE_DEVIATION"
        double catalogPrice,
        double enteredPrice,
        double percentageDifference
) {
    public static PriceValidationResult ok(double catalogPrice, double enteredPrice) {
        return new PriceValidationResult(false, null, catalogPrice, enteredPrice, 0);
    }

    public static PriceValidationResult warn(double catalogPrice, double enteredPrice, double pct) {
        return new PriceValidationResult(true, "PRICE_DEVIATION", catalogPrice, enteredPrice, pct);
    }
}
