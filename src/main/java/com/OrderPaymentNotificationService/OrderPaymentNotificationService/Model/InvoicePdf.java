package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.Data;

/** Cloudinary-hosted PDF for a finalized {@link Invoice}, kept in its own table (mirrors Receipt). */
@Entity
@Table(name = "invoice_pdfs")
@Data
public class InvoicePdf {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", unique = true, nullable = false)
    private UUID invoiceId;

    @Column(name = "pdf_url", nullable = false)
    private String pdfUrl;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private Instant generatedAt;
}
