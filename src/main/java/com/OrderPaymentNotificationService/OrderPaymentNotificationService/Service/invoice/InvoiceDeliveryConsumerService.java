package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.InvoiceDeliveryEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Invoice;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceDelivery;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoicePdf;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InvoiceDeliveryRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InvoicePdfRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InvoiceRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.NotificationFactory;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles {@code invoice.delivery.requested}: loads the invoice's PDF, hands
 * it to the right NotificationService bean (email vs WhatsApp), and records
 * the outcome on the InvoiceDelivery row. Errors are swallowed (recorded as
 * FAILED) rather than rethrown — a delivery failure shouldn't cause endless
 * Kafka redelivery of what is, in the worst case, a one-off provider outage;
 * the seller sees FAILED and can just tap Send again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceDeliveryConsumerService {

    private final ObjectMapper objectMapper;
    private final InvoiceRepository invoiceRepo;
    private final InvoicePdfRepository pdfRepo;
    private final InvoiceDeliveryRepository deliveryRepo;
    private final NotificationFactory notificationFactory;
    private final InvoicePdfUploadService pdfUploadService;

    public void handleDeliveryEvent(String message) {
        InvoiceDeliveryEvent event;
        try {
            event = objectMapper.readValue(message, InvoiceDeliveryEvent.class);
        } catch (Exception e) {
            log.error("Invoice delivery event payload could not be parsed — dropping: {}", e.getMessage(), e);
            return;
        }

        log.info("Invoice delivery event received | invoiceId={} channel={}", event.getInvoiceId(), event.getChannel());

        Optional<InvoiceDelivery> deliveryOpt = deliveryRepo.findById(event.getDeliveryId());
        if (deliveryOpt.isEmpty()) {
            log.warn("Invoice delivery row not found — dropping | deliveryId={}", event.getDeliveryId());
            return;
        }
        InvoiceDelivery delivery = deliveryOpt.get();

        Optional<Invoice> invoiceOpt = invoiceRepo.findById(event.getInvoiceId());
        Optional<InvoicePdf> pdfOpt = pdfRepo.findByInvoiceId(event.getInvoiceId());
        if (invoiceOpt.isEmpty() || pdfOpt.isEmpty()) {
            markFailed(delivery, "Invoice or its PDF was not found");
            return;
        }
        Invoice invoice = invoiceOpt.get();

        File tempFile = null;
        try {
            String beanName = "WHATSAPP".equals(event.getChannel())
                    ? "whatsappNotificationService" : "emailNotificationService";
            NotificationService service = notificationFactory.getService(beanName);
            if (service == null) {
                markFailed(delivery, beanName + " is not registered");
                return;
            }

            tempFile = File.createTempFile("invoice-" + invoice.getInvoiceNumber(), ".pdf");
            // Fetched via a freshly signed Cloudinary URL rather than the stored
            // plain URL — Cloudinary blocks unauthenticated delivery of raw PDFs.
            Files.write(tempFile.toPath(), pdfUploadService.downloadBytes(event.getInvoiceId()));

            String subject = "Invoice " + invoice.getInvoiceNumber();
            String body = "Please find attached your invoice " + invoice.getInvoiceNumber() + ".";
            service.sendNotification(event.getDestination(), subject, body, tempFile);

            markSent(delivery);
        } catch (Exception e) {
            log.error("Invoice delivery failed | invoiceId={} channel={} error={}",
                    event.getInvoiceId(), event.getChannel(), e.getMessage(), e);
            markFailed(delivery, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } finally {
            if (tempFile != null) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    private void markSent(InvoiceDelivery delivery) {
        delivery.setStatus(InvoiceDelivery.Status.SENT);
        delivery.setSentAt(Instant.now());
        deliveryRepo.save(delivery);
    }

    private void markFailed(InvoiceDelivery delivery, String reason) {
        delivery.setStatus(InvoiceDelivery.Status.FAILED);
        delivery.setFailedAt(Instant.now());
        delivery.setFailureReason(reason);
        deliveryRepo.save(delivery);
    }
}
