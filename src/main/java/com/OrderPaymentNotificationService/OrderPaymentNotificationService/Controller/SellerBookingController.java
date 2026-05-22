package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.SellerBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SellerBookingController {

    private final SellerBookingService sellerBookingService;

    // ── GET /api/v1/seller/orders?page=0&size=20&status=ALL ──────────────────
    @GetMapping("/api/v1/seller/orders")
    public ResponseEntity<?> getShopOrders(
            @RequestParam(defaultValue = "0")   int    page,
            @RequestParam(defaultValue = "20")  int    size,
            @RequestParam(defaultValue = "ALL") String status) {
        ApiResponse<Object> res = sellerBookingService.getShopOrders(page, size, status);
        return ResponseEntity.status(res.statusCode()).body(res);
    }

    // ── PUT /api/v1/booking/{bookingId}/status  body: { "status": "PROCESSING" }
    @PutMapping("/api/v1/booking/{bookingId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable UUID bookingId,
            @RequestBody   Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "status field is required", null, 400));
        }
        try {
            ApiResponse<Object> res = sellerBookingService.updateOrderStatus(bookingId, newStatus);
            return ResponseEntity.status(res.statusCode()).body(res);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, e.getMessage(), null, 404));
        }
    }

    // ── GET /api/v1/seller/orders/{bookingId} ─────────────────────────────────
    @GetMapping("/api/v1/seller/orders/{bookingId}")
    public ResponseEntity<?> getSellerOrderDetail(@PathVariable UUID bookingId) {
        try {
            ApiResponse<Object> res = sellerBookingService.getSellerOrderDetail(bookingId);
            return ResponseEntity.status(res.statusCode()).body(res);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, e.getMessage(), null, 404));
        }
    }

    // ── GET /api/v1/seller/stats?days=7 ──────────────────────────────────────
    @GetMapping("/api/v1/seller/stats")
    public ResponseEntity<?> getShopStats(
            @RequestParam(defaultValue = "7") int days) {
        ApiResponse<Object> res = sellerBookingService.getShopStats(days);
        return ResponseEntity.status(res.statusCode()).body(res);
    }

    // ── GET /api/v1/seller/stats/top-products?limit=5 ────────────────────────
    @GetMapping("/api/v1/seller/stats/top-products")
    public ResponseEntity<?> getTopProducts(
            @RequestParam(defaultValue = "5") int limit) {
        ApiResponse<Object> res = sellerBookingService.getTopProducts(limit);
        return ResponseEntity.status(res.statusCode()).body(res);
    }

    // ── GET /api/v1/seller/orders/status-counts ──────────────────────────────
    @GetMapping("/api/v1/seller/orders/status-counts")
    public ResponseEntity<?> getStatusCounts() {
        ApiResponse<Object> res = sellerBookingService.getStatusCounts();
        return ResponseEntity.status(res.statusCode()).body(res);
    }

    // ── GET /api/v1/seller/earnings ───────────────────────────────────────────
    @GetMapping("/api/v1/seller/earnings")
    public ResponseEntity<?> getSellerEarnings() {
        ApiResponse<Object> res = sellerBookingService.getSellerEarnings();
        return ResponseEntity.status(res.statusCode()).body(res);
    }
}
