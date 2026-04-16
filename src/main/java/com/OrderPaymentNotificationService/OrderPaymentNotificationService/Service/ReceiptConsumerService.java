package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.receipt.ReceiptEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Receipt;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.DateTimeUtil;

/**
 * Kafka consumer for the {@code order.receipt.generate} topic.
 *
 * Flow:
 *  1. Deserialise JSON → ReceiptEvent
 *  2. Idempotency guard — skip if receipt already saved for this booking
 *  3. Generate PDF bytes via ReceiptGeneratorService
 *  4. Persist Receipt entity
 *  5. Acknowledge Kafka offset (manual ACK mode)
 *
 * On failure the offset is NOT acknowledged, so the message is retried on
 * the next poll.  A dead-letter topic (order.receipt.dlq) can be wired in
 * later for messages that keep failing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptConsumerService {


    private final ObjectMapper             objectMapper;
    private final ReceiptGeneratorService  generatorService;
    private final ReceiptRepository        receiptRepository;

    // ══════════════════════════════════════════════════════════════════════════
    //  Kafka listener
    // ══════════════════════════════════════════════════════════════════════════

    @KafkaListener(
            topics            = ReceiptProducerService.TOPIC,
            groupId           = "receipt-generator-group",
            containerFactory  = "receiptListenerContainerFactory"
    )
    @Transactional
    public void handleReceiptEvent(String message, Acknowledgment ack) {
        ReceiptEvent event = null;
        try {
            event = objectMapper.readValue(message, ReceiptEvent.class);
            log.info("Receipt event received | bookingId={}", event.getBookingId());

            // Idempotency — guard against duplicate delivery
            if (receiptRepository.existsByBookingId(event.getBookingId())) {
                log.info("Receipt already generated (idempotent skip) | bookingId={}", event.getBookingId());
                ack.acknowledge();
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

            ack.acknowledge();   // commit offset only after successful save

        } catch (Exception e) {
            log.error("Receipt generation failed — offset NOT acknowledged (will retry) | bookingId={}",
                    event != null ? event.getBookingId() : "UNKNOWN", e);
            // No ack → Kafka will redeliver this message on the next poll
        }
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
