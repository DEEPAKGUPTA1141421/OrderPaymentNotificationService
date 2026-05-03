package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.delivery.BookingConfirmedEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmedProducer {

    static final String TOPIC = "booking.confirmed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(Booking booking) {
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(booking.getId())
                .customerId(booking.getUserId())
                .shopId(booking.getShopId())
                .deliveryAddressId(booking.getDeliveryAddress())
                .totalAmountPaise(booking.getTotalAmount())
                .confirmedAt(Instant.now())
                .build();

        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, booking.getId().toString(), json);
            log.info("BookingConfirmedEvent published | bookingId={} customerId={}",
                    booking.getId(), booking.getUserId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise BookingConfirmedEvent | bookingId={}", booking.getId(), e);
        }
    }
}
