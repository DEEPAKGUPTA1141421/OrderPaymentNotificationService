package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Columns;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sponsored_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsoredBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shop_id")
    private UUID shop; // who bought this slot

    @Column(name = "product_id")
    private UUID product; // optional → if promoting a product

    private String brandName; // optional → if promoting a brand
    private Status status;

    @OneToOne
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private SponsoredSlot slot;
    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private boolean active;

    public enum Status {
        INTIATED,
        SUCCESS,
        FAILED,
        ABONDONED,
    }
}
