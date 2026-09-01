package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.Data;

/** One delivery attempt of a finalized invoice over a channel (WhatsApp/Email). */
@Entity
@Table(name = "invoice_deliveries")
@Data
public class InvoiceDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    /** Phone number (WhatsApp) or email address this attempt targeted. */
    @Column(nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    private Instant sentAt;
    private Instant deliveredAt;
    private Instant failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum Channel { WHATSAPP, EMAIL }

    public enum Status { PENDING, SENT, DELIVERED, FAILED }
}
