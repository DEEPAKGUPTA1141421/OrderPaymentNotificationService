package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Payment.java
@Entity
@Table(name = "payments")
@Data
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING; // PENDING, SUCCESS, FAILED

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions = new ArrayList<>();

    @Column(nullable = false)
    private String totalAmount; // in paise
    private String paidAmount = "0";

    public enum Status {
        PENDING, SUCCESS, FAILED
    }
}