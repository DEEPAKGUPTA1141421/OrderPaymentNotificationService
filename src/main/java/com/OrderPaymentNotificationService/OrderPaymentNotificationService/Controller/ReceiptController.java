package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Exposes the receipt (invoice) download endpoints.
 *
 * GET /api/v1/receipt/{bookingId}/download
 *   → buyer-facing download → returns a PDF file (Content-Disposition: attachment)
 *   → 404 if receipt not yet generated
 *   → 403 if booking belongs to a different user
 *
 * GET /api/v1/seller/receipt/{bookingId}/download
 *   → seller-facing download of the invoice for one of their own orders
 *   → 404 if booking or receipt not found
 *   → 403 if booking does not belong to this seller
 *
 * Security: ROLE_USER / authenticated, enforced in WebConfig + service-level ownership check.
 */
@RestController
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/api/v1/receipt/{bookingId}/download")
    public ResponseEntity<?> downloadReceipt(@PathVariable UUID bookingId) {
        try {
            return receiptService.downloadReceipt(bookingId);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, e.getMessage(), null, 404));
        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .body(new ApiResponse<>(false, "Access denied", null, 403));
        }
    }

    @GetMapping("/api/v1/seller/receipt/{bookingId}/download")
    public ResponseEntity<?> downloadReceiptForSeller(@PathVariable UUID bookingId) {
        try {
            return receiptService.downloadReceiptForSeller(bookingId);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, e.getMessage(), null, 404));
        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .body(new ApiResponse<>(false, "Access denied", null, 403));
        }
    }
}
