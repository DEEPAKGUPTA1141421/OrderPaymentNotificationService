package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.Data;

/**
 * A lightweight per-seller customer book — walk-in / offline customers a
 * seller invoices directly, independent of the buyer-app user model.
 */
@Entity
@Table(name = "invoice_customers")
@Data
public class InvoiceCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String email;
    private String gstin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
