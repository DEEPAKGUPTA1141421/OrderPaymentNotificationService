package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Per-seller, per-year running counter backing invoice number generation
 * (INV-{year}-{seq}). Incremented under a pessimistic row lock — see
 * InvoiceNumberService — so two concurrent finalize requests can never hand
 * out the same number.
 */
@Entity
@Table(name = "invoice_counters", uniqueConstraints = @UniqueConstraint(columnNames = {"seller_id", "year"}))
@Data
public class InvoiceCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private int year;

    @Column(name = "last_value", nullable = false)
    private long lastValue;
}
