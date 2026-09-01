package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Receipt;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
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
 * Handles receipt (invoice) download requests.
 * Extends BaseService to verify the authenticated principal owns the receipt —
 * either as the buyer (userId on the Receipt) or the seller (shopId on the Booking).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService extends BaseService {

    private final ReceiptRepository receiptRepository;
    private final BookingRepository bookingRepository;

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

    /**
     * Seller-side counterpart of {@link #downloadReceipt(UUID)}. Ownership is verified
     * against the booking's shopId (the seller's principal id) rather than the receipt's
     * buyer userId — a seller downloading the invoice for one of their own orders.
     * Throws {@link NoSuchElementException} if the booking or its receipt does not exist.
     * Throws {@link SecurityException} if the booking does not belong to this seller.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadReceiptForSeller(UUID bookingId) {
        UUID shopId = getUserId();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));

        if (!booking.getShopId().equals(shopId)) {
            log.warn("Seller receipt access denied | shopId={} bookingId={}", shopId, bookingId);
            throw new SecurityException("Access denied");
        }

        Receipt receipt = receiptRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Invoice not yet generated for booking: " + bookingId +
                        ". It may still be processing — please retry in a few seconds."));

        String filename = receipt.getInvoiceNumber() + ".pdf";

        log.info("Seller invoice download | shopId={} bookingId={} invoice={}",
                shopId, bookingId, receipt.getInvoiceNumber());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(receipt.getPdfBytes().length)
                .body(receipt.getPdfBytes());
    }
}
