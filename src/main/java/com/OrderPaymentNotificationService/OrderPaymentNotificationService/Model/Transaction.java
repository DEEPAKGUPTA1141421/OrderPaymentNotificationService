package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "transactions")
@Data
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    private Method method; // POINTS, GATEWAY

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING; // PENDING, SUCCESS, FAILED

    @Column(nullable = false)
    private String amount; // in paise

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    public enum Method {
        POINTS, GATEWAY
    }

    public enum Status {
        PENDING, SUCCESS, FAILED
    }
}
