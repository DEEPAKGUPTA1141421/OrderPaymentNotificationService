package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.Data;

/**
 * A seller-authored (POS-style) invoice — distinct from the auto-generated
 * {@link Receipt} that's created for buyer-app orders. A seller builds one of
 * these manually: catalog items, ad-hoc "custom" items for un-onboarded
 * products, an optional walk-in customer, and sends it out over WhatsApp/Email.
 */
@Entity
@Table(name = "invoices")
@Data
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owning seller — the authenticated principal id (shopId). */
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    /** Human-readable number, e.g. INV-2026-000124. Assigned on finalize (null while DRAFT). */
    @Column(name = "invoice_number", length = 32)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private InvoiceCustomer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    // ── Totals — paise, computed server-side from items on every save ────────
    @Column(name = "subtotal_paise", nullable = false)
    private long subtotalPaise;

    @Column(name = "discount_paise", nullable = false)
    private long discountPaise;

    @Column(name = "tax_paise", nullable = false)
    private long taxPaise;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    private Instant issuedAt;
    private Instant dueAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Status {
        DRAFT, FINALIZED, SENT, VIEWED, PAID, PARTIALLY_PAID, OVERDUE, CANCELLED;

        public boolean canTransitionTo(Status next) {
            return switch (this) {
                case DRAFT -> next == FINALIZED || next == CANCELLED;
                case FINALIZED -> next == SENT || next == CANCELLED;
                case SENT -> next == VIEWED || next == PAID || next == PARTIALLY_PAID
                        || next == OVERDUE || next == CANCELLED;
                case VIEWED -> next == PAID || next == PARTIALLY_PAID || next == OVERDUE || next == CANCELLED;
                case PARTIALLY_PAID -> next == PAID || next == OVERDUE || next == CANCELLED;
                case OVERDUE -> next == PAID || next == PARTIALLY_PAID || next == CANCELLED;
                case PAID -> false;      // terminal
                case CANCELLED -> false; // terminal
            };
        }

        public void assertCanTransitionTo(Status next) {
            if (!canTransitionTo(next)) {
                throw new IllegalStateException(
                        "Invalid invoice status transition: " + this.name() + " → " + next.name());
            }
        }
    }
}
