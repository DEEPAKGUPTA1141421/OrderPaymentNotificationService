package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.BookingQueryService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.BookingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService      bookingService;
    private final BookingQueryService bookingQueryService;

    // ══════════════════════════════════════════════════════════════════════════
    //  POST /api/v1/booking/checkout
    //  Creates one booking per shop from the user's active cart.
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestParam("deliveryAddress") UUID deliveryAddress) {
        ApiResponse<Object> response = bookingService.createBookingFromCart(deliveryAddress);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/booking
    //  Paginated order history for the authenticated user — order list page.
    //
    //  Query params:
    //    page  (int, default 0)  — zero-based page number
    //    size  (int, default 10) — items per page, clamped to max 50
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<?> getMyOrders(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        ApiResponse<Object> response = bookingQueryService.getMyOrders(page, size);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/booking/{bookingId}
    //  Full details for a single order — order detail page.
    //  Returns 403 if the booking belongs to a different user.
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/{bookingId}")
    public ResponseEntity<?> getOrderDetail(@PathVariable UUID bookingId) {
        try {
            ApiResponse<Object> response = bookingQueryService.getOrderDetail(bookingId);
            return ResponseEntity.status(response.statusCode()).body(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, e.getMessage(), null, 404));
        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .body(new ApiResponse<>(false, "Access denied", null, 403));
        }
    }
}
