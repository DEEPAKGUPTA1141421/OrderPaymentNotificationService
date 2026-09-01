package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.PriceValidationResult;

/**
 * Flags an invoice line price that looks like a mistake (a missing zero, a
 * fat-fingered digit) rather than a deliberate discount/markup — advisory
 * only, never a hard block. A deviation only matters if it's large in BOTH
 * relative and absolute terms: a 50% swing on a ₹20 item isn't worth a
 * warning, but the same swing on a ₹5,000 item almost certainly is.
 */
@Service
public class PriceValidationService {

    private static final double PERCENTAGE_THRESHOLD = 50.0; // %
    private static final double ABSOLUTE_THRESHOLD_RUPEES = 100.0;

    public PriceValidationResult validate(double catalogPrice, double enteredPrice) {
        if (catalogPrice <= 0) {
            return PriceValidationResult.ok(catalogPrice, enteredPrice);
        }

        double absDiff = Math.abs(enteredPrice - catalogPrice);
        double pctDiff = (absDiff / catalogPrice) * 100.0;

        if (pctDiff >= PERCENTAGE_THRESHOLD && absDiff >= ABSOLUTE_THRESHOLD_RUPEES) {
            return PriceValidationResult.warn(catalogPrice, enteredPrice, Math.round(pctDiff * 10.0) / 10.0);
        }
        return PriceValidationResult.ok(catalogPrice, enteredPrice);
    }
}
