package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Seller-facing notification copy for order/payment events, wrapping
 * {@link NotificationDispatcher#dispatch}. Keeps the "what to say" templating
 * in one place while call sites (the payment gateway strategies) stay one-liners.
 */
@Service
@RequiredArgsConstructor
public class SellerNotificationEvents {

    private final NotificationDispatcher dispatcher;

    public void notifyNewOrder(Booking booking) {
        String shortId = shortId(booking);
        dispatcher.dispatch(
                booking.getShopId(),
                NotificationCategory.ORDER_UPDATES,
                "New order received",
                "You've received a new order #" + shortId + ".",
                "/orders/" + booking.getId(),
                booking.getId().toString(),
                Map.of("orderId", booking.getId().toString(), "type", "NEW_ORDER"));
    }

    public void notifyPaymentReceived(Booking booking) {
        String shortId = shortId(booking);
        String rupees = toRupeesStr(booking.getTotalAmount());
        dispatcher.dispatch(
                booking.getShopId(),
                NotificationCategory.PAYMENT_UPDATES,
                "Payment received",
                "₹" + rupees + " received for order #" + shortId + ".",
                "/orders/" + booking.getId(),
                booking.getId().toString(),
                Map.of("orderId", booking.getId().toString(), "type", "PAYMENT_RECEIVED"));
    }

    public void notifyPaymentFailed(Booking booking) {
        String shortId = shortId(booking);
        dispatcher.dispatch(
                booking.getShopId(),
                NotificationCategory.PAYMENT_UPDATES,
                "Payment failed",
                "Payment for order #" + shortId + " could not be completed.",
                "/orders/" + booking.getId(),
                booking.getId().toString(),
                Map.of("orderId", booking.getId().toString(), "type", "PAYMENT_FAILED"));
    }

    private String shortId(Booking booking) {
        return booking.getId().toString().substring(0, 8).toUpperCase();
    }

    private String toRupeesStr(String paise) {
        if (paise == null || paise.isBlank()) return "0.00";
        try {
            return new BigDecimal(paise)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (NumberFormatException e) {
            return "0.00";
        }
    }
}
