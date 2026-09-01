package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.invoice;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.invoice.InvoiceDeliveryEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.messaging.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes to {@code invoice.delivery.requested} — decouples PDF/WhatsApp/Email
 * dispatch from the "Send" request path, same pattern as ReceiptProducerService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceDeliveryProducerService {

    public static final String TOPIC = "invoice.delivery.requested";

    private final EventPublisher eventPublisher;
    private final ObjectMapper   objectMapper;

    public void publish(UUID invoiceId, UUID deliveryId, String channel, String destination) {
        try {
            InvoiceDeliveryEvent event = new InvoiceDeliveryEvent(invoiceId, deliveryId, channel, destination);
            String json = objectMapper.writeValueAsString(event);
            eventPublisher.publish(TOPIC, invoiceId.toString(), json);
            log.info("Invoice delivery event published | invoiceId={} channel={}", invoiceId, channel);
        } catch (Exception e) {
            log.error("Failed to publish invoice delivery event | invoiceId={}", invoiceId, e);
        }
    }
}
