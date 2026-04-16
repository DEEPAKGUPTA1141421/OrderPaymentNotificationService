package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Exposes the receipt download endpoint.
 *
 * GET /api/v1/receipt/{bookingId}/download
 *   → returns a PDF file download (Content-Disposition: attachment)
 *   → 404 if receipt not yet generated
 *   → 403 if booking belongs to a different user
 *
 * Security: ROLE_USER, enforced in WebConfig + service-level ownership check.
 */
@RestController
@RequestMapping("/api/v1/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/{bookingId}/download")
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
}
