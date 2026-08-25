package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.receipt.ReceiptEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Receipt;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.DateTimeUtil;

/**
 * Handles the {@code order.receipt.generate} topic. Delivered via Kafka or
 * Redis depending on app.messaging.provider (see KafkaMessagingListenerConfig
 * / RedisMessagingListenerConfig).
 *
 * Flow:
 *  1. Deserialise JSON → ReceiptEvent
 *  2. Idempotency guard — skip if receipt already saved for this booking
 *  3. Generate PDF bytes via ReceiptGeneratorService
 *  4. Persist Receipt entity
 *
 * On failure this method throws (rather than swallowing), so the Kafka
 * registrar skips acknowledging the offset and the message is retried on
 * the next poll — same "no ack on failure" semantics as before. Under
 * Redis pub/sub there is no such redelivery; a failure there is just
 * logged and dropped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptConsumerService {


    private final ObjectMapper             objectMapper;
    private final ReceiptGeneratorService  generatorService;
    private final ReceiptRepository        receiptRepository;

    @Transactional
    public void handleReceiptEvent(String message) {
        ReceiptEvent event;
        try {
            event = objectMapper.readValue(message, ReceiptEvent.class);
        } catch (Exception e) {
            log.error("Receipt event payload could not be parsed — dropping (not retried): {}", e.getMessage(), e);
            return;
        }

        log.info("Receipt event received | bookingId={}", event.getBookingId());

        // Idempotency — guard against duplicate delivery
        if (receiptRepository.existsByBookingId(event.getBookingId())) {
            log.info("Receipt already generated (idempotent skip) | bookingId={}", event.getBookingId());
            return;
        }

        String invoiceNumber = buildInvoiceNumber(event);
        byte[] pdfBytes      = generatorService.generatePdf(event, invoiceNumber);

        Receipt receipt = new Receipt();
        receipt.setBookingId(event.getBookingId());
        receipt.setUserId(event.getUserId());
        receipt.setInvoiceNumber(invoiceNumber);
        receipt.setPdfBytes(pdfBytes);
        receiptRepository.save(receipt);

        log.info("Receipt saved | bookingId={} invoiceNumber={} sizeBytes={}",
                event.getBookingId(), invoiceNumber, pdfBytes.length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Builds a human-readable invoice number.
     * Format: INV-{YYYYMM}-{FIRST-8-CHARS-OF-BOOKING-UUID}
     * Example: INV-202501-D2F3A1B0
     */
    private String buildInvoiceNumber(ReceiptEvent event) {
        String yearMonth = DateTimeUtil.invoiceMonthKey();
        String suffix    = event.getBookingId().toString()
                .substring(0, 8).toUpperCase();
        return "INV-" + yearMonth + "-" + suffix;
    }
}
