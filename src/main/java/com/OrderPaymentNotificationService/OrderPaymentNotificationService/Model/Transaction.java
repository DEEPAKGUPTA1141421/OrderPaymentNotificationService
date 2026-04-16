package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.ZonedDateTime;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.DateTimeUtil;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonBackReference;

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
    @JsonBackReference
    private Payment payment;

    @Enumerated(EnumType.STRING)
    private Method method; // POINTS, GATEWAY

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING; // PENDING, SUCCESS, FAILED

    @Column(nullable = false)
    private String amount; // in paise

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "transcation_number")
    private UUID transcationNumber;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = DateTimeUtil.nowIst();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = DateTimeUtil.nowIst();

    public enum Method {
        POINTS, GATEWAY, COD
    }

    public enum Status {
        INITIATED, PENDING, SUCCESS, FAILED, REVERSED, REVERSED_FAILED, ABONDENED
    }
}
