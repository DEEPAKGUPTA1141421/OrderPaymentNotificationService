package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Receipt;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Handles receipt download requests.
 * Extends BaseService to verify the authenticated user owns the receipt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService extends BaseService {

    private final ReceiptRepository receiptRepository;

    /**
     * Returns the PDF bytes for the given booking as a file-download response.
     * Throws {@link NoSuchElementException} if the receipt has not been generated yet.
     * Throws {@link SecurityException} if the booking does not belong to the current user.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadReceipt(UUID bookingId) {
        UUID userId = getUserId();

        // First check if receipt exists at all (better error message)
        Receipt receipt = receiptRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Receipt not yet generated for booking: " + bookingId +
                        ". It may still be processing — please retry in a few seconds."));

        // Ownership check
        if (!receipt.getUserId().equals(userId)) {
            log.warn("Receipt access denied | userId={} bookingId={}", userId, bookingId);
            throw new SecurityException("Access denied");
        }

        String filename = receipt.getInvoiceNumber() + ".pdf";

        log.info("Receipt download | userId={} bookingId={} invoice={}",
                userId, bookingId, receipt.getInvoiceNumber());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(receipt.getPdfBytes().length)
                .body(receipt.getPdfBytes());
    }
}
