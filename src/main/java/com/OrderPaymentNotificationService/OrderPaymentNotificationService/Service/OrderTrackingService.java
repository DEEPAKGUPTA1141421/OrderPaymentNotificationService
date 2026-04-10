package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service class for order tracking operations.
 * Handles business logic for retrieving booking and payment tracking information.
 */
@Service
@RequiredArgsConstructor
public class OrderTrackingService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Fetch tracking data for a booking.
     *
     * @param bookingId UUID of the booking
     * @return Map containing booking and payment tracking data, or null if booking not found
     */
    public Map<String, Object> getTrackingData(UUID bookingId) {
        // Find booking
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return null;
        }

        // Find matching payment (first by bookingId)
        Payment payment = paymentRepository
                .findAll()
                .stream()
                .filter(p -> p.getBookingId().equals(bookingId))
                .findFirst()
                .orElse(null);

        // Determine payment status
        String paymentStatus = payment != null ? payment.getStatus().name() : "PENDING";
        String clientPaymentStatus = mapPaymentStatus(paymentStatus);

        // Build tracking data response
        return buildTrackingDataMap(booking, clientPaymentStatus);
    }

    /**
     * Map internal payment status to simplified client status.
     *
     * @param paymentStatus internal payment status
     * @return simplified payment status for client
     */
    private String mapPaymentStatus(String paymentStatus) {
        return switch (paymentStatus) {
            case "SUCCESS" -> "SUCCESS";
            case "INITIATED", "PENDING" -> "PENDING";
            case "FAILED", "REVERSED", "REVERSED_FAILED", "ABONDENED" -> "FAILED";
            default -> paymentStatus;
        };
    }

    /**
     * Build tracking data map from booking information and payment status.
     *
     * @param booking the booking object
     * @param clientPaymentStatus the mapped payment status
     * @return Map containing tracking data
     */
    private Map<String, Object> buildTrackingDataMap(Booking booking, String clientPaymentStatus) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", booking.getId().toString());
        data.put("status", booking.getStatus().name());
        data.put("paymentStatus", clientPaymentStatus);
        data.put("totalAmount", booking.getTotalAmount());
        data.put("shopId", booking.getShopId().toString());
        data.put("deliveryAddress", booking.getDeliveryAddress().toString());
        data.put("items", booking.getItems().stream().map(item -> {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("productId", item.getProductId().toString());
            itemMap.put("variantId", item.getVariantId().toString());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("price", item.getPrice());
            return itemMap;
        }).toList());
        return data;
    }
}
