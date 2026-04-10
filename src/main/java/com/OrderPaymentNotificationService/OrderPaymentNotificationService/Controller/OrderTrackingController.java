package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.OrderTrackingService;

import lombok.RequiredArgsConstructor;

/**
 * Order Tracking API
 *
 * GET /api/v1/booking/{bookingId}/tracking
 *   Returns booking status + payment status in one call for the Flutter
 *   order-tracking screen.
 *
 * Auth: requires valid JWT (ROLE_USER).
 *       The booking is validated to belong to the authenticated user via
 *       the X-User-Id header set by the JWT filter.
 */
@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class OrderTrackingController {

    private final OrderTrackingService orderTrackingService;

    /**
     * Fetch tracking data for a booking.
     *
     * Response shape:
     * {
     *   "bookingId": "uuid",
     *   "status": "CONFIRMED",
     *   "paymentStatus": "SUCCESS",
     *   "totalAmount": "2187.84",
     *   "items": [ { "productId": ..., "quantity": 2, "price": "999.0" } ],
     *   "createdAt": "2026-04-10T10:00:00Z"
     * }
     */
    @GetMapping("/{bookingId}/tracking")
    public ResponseEntity<ApiResponse<Object>> getTracking(
            @PathVariable UUID bookingId) {

        // Fetch tracking data from service
        Object data = orderTrackingService.getTrackingData(bookingId);
        
        if (data == null) {
            return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "Booking not found", null, 404));
        }

        return ResponseEntity.ok(new ApiResponse<>(true, "Tracking fetched", data, 200));
    }
}