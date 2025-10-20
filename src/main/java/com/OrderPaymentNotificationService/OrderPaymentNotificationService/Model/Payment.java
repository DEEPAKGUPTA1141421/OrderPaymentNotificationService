package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonManagedReference;

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
    private Status status;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Transaction> transactions = new ArrayList<>();

    @Column(nullable = false)
    private String totalAmount; // in paise
    private String paidAmount = "0";

    public enum Status {
        INITIATED, PENDING, SUCCESS, FAILED, REVERSED, REVERSED_FAILED, ABONDENED
    }

    @PostPersist
    public void checkTranscationStatus() {
    }
}