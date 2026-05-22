package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.delivery.BookingConfirmedEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.receipt.ReceiptEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Loads booking + payment data, builds a {@link ReceiptEvent}, and publishes
 * it to the {@code order.receipt.generate} Kafka topic.
 *
 * Called by payment gateways as soon as a booking is confirmed:
 *   – CodPaymentGateway  → after booking set to CONFIRMED
 *   – PhonePePaymentGateway.handleWebhook → after payment marked SUCCESS
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptProducerService {

    static final String TOPIC = "order.receipt.generate";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;
    private final BookingRepository             bookingRepository;
    private final PaymentRepository             paymentRepository;
    private final BookingConfirmedProducer      bookingConfirmedProducer;

    // ══════════════════════════════════════════════════════════════════════════
    //  Public API — called by gateways
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void publishForBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            log.warn("Receipt event skipped — booking not found: {}", bookingId);
            return;
        }

        Payment payment = paymentRepository.findFirstByBookingId(bookingId).orElse(null);

        bookingConfirmedProducer.publish(booking);

        ReceiptEvent event = buildEvent(booking, payment);
        sendEvent(event);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    private ReceiptEvent buildEvent(Booking booking, Payment payment) {
        List<ReceiptEvent.ReceiptItemEvent> itemEvents = booking.getItems().stream()
                .map(item -> {
                    long lineTotal = Long.parseLong(item.getPrice()) * item.getQuantity();
                    return new ReceiptEvent.ReceiptItemEvent(
                            item.getProductId(),
                            item.getVariantId(),
                            item.getQuantity(),
                            item.getPrice(),
                            String.valueOf(lineTotal)
                    );
                })
                .toList();

        return new ReceiptEvent(
                booking.getId(),
                payment != null ? payment.getId()          : null,
                booking.getUserId(),
                booking.getShopId(),
                payment != null ? derivePaymentMode(payment) : "COD",
                booking.getTotalAmount(),
                payment != null ? payment.getPaidAmount()  : "0",
                Instant.now(),
                itemEvents
        );
    }

    private String derivePaymentMode(Payment payment) {
        if (payment.getTransactions() == null || payment.getTransactions().isEmpty()) {
            return "UNKNOWN";
        }
        boolean hasCod     = payment.getTransactions().stream()
                .anyMatch(t -> t.getMethod().name().equals("COD"));
        boolean hasGateway = payment.getTransactions().stream()
                .anyMatch(t -> t.getMethod().name().equals("GATEWAY"));
        boolean hasPoints  = payment.getTransactions().stream()
                .anyMatch(t -> t.getMethod().name().equals("POINTS"));

        if (hasCod)                   return "COD";
        if (hasGateway && hasPoints)  return "MIXED";
        if (hasGateway)               return "ONLINE";
        if (hasPoints)                return "POINTS";
        return "UNKNOWN";
    }

    private void sendEvent(ReceiptEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getBookingId().toString(), json);
            log.info("Receipt event published | bookingId={} paymentMode={}",
                    event.getBookingId(), event.getPaymentMode());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise ReceiptEvent | bookingId={}", event.getBookingId(), e);
        }
    }
}
