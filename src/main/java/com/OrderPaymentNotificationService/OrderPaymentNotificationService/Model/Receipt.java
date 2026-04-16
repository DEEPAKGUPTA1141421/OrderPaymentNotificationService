package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores the generated GST tax invoice PDF for a confirmed booking.
 * One receipt per booking (enforced by the UNIQUE constraint on booking_id).
 * The consumer writes here; the download endpoint reads from here.
 */
@Entity
@Table(name = "receipts")
@Data
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique link back to the booking that triggered this receipt. */
    @Column(name = "booking_id", unique = true, nullable = false)
    private UUID bookingId;

    /** Owner — used by the download endpoint to verify access. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Human-readable invoice number, e.g. INV-202501-D2F3A1B0. */
    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    /** Raw PDF bytes. Stored in the DB so no external file service is required. */
    @Lob
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "pdf_bytes", nullable = false)
    private byte[] pdfBytes;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private Instant generatedAt;
}
