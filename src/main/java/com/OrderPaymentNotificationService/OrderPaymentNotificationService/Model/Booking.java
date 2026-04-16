
package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
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
    private Status status = Status.INITIATED; // PENDING, CONFIRMED, CANCELLED, FAILED

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    @Column(nullable = false)
    private String totalAmount; // stored in paise as String

    @Column(nullable = false)
    private Instant expiresAt; // 5 min hold

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum Status {
        INITIATED,        // booking created, awaiting payment
        CONFIRMED,        // payment successful, seller notified
        PROCESSING,       // seller accepted and is preparing the order
        OUT_FOR_DELIVERY, // order picked up by delivery agent
        DELIVERED,        // order handed to customer — terminal success
        CANCELLED,        // cancelled before dispatch
        FAILED,           // payment failed
        REVERSED,         // post-payment reversal completed
        REVERSE_FAILED;   // reversal attempted but failed

        /**
         * Enforces a strict state machine — no arbitrary jumps allowed.
         * Call before any status update to prevent invalid transitions.
         */
        public boolean canTransitionTo(Status next) {
            return switch (this) {
                case INITIATED        -> next == CONFIRMED   || next == CANCELLED || next == FAILED;
                case CONFIRMED        -> next == PROCESSING  || next == CANCELLED || next == REVERSED;
                case PROCESSING       -> next == OUT_FOR_DELIVERY || next == CANCELLED;
                case OUT_FOR_DELIVERY -> next == DELIVERED   || next == REVERSED;
                case DELIVERED        -> next == REVERSED;   // e.g. return/refund after delivery
                case CANCELLED        -> false;              // terminal
                case FAILED           -> false;              // terminal
                case REVERSED         -> next == REVERSE_FAILED; // retry path only
                case REVERSE_FAILED   -> next == REVERSED;  // allow retry
            };
        }
        

        /**
         * Convenience guard — throws if the transition is not allowed.
         */
        public void assertCanTransitionTo(Status next) {
            if (!canTransitionTo(next)) {
                throw new IllegalStateException(
                        "Invalid order status transition: " + this.name() + " → " + next.name());
            }
        }
    }

    @PostPersist
    public void sendEmail() {
        // send email logic
        System.out.println("Booking created with ID: " + id);
    }
}
