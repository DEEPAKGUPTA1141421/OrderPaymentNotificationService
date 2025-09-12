package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

// BookingItem.java
@Entity
@Table(name = "booking_items")
@Data
public class BookingItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID variantId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String price; // unit price in paise
}
