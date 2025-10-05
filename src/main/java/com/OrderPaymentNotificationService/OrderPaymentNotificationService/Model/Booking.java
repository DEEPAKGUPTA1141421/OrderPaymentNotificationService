
package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

// Booking.java
@Entity
@Table(name = "bookings")
@Data
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID shopId;

    @Column(nullable = false)
    private UUID deliveryAddress;

    @Enumerated(EnumType.STRING)
    private Status status = Status.Initiated; // PENDING, CONFIRMED, CANCELLED, FAILED

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    @Column(nullable = false)
    private String totalAmount; // stored in paise as String

    @Column(nullable = false)
    private Instant expiresAt; // 5 min hold

    public enum Status {
        Initiated, CONFIRMED, CANCELLED, FAILED, REVERSE, REVERSE_FAILED
    }

    @PostPersist
    public void sendEmail() {
        // send email logic
        System.out.println("Booking created with ID: " + id);
    }
}
