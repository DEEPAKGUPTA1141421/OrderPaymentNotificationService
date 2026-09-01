package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.Data;

/**
 * One line on an {@link Invoice}. Deliberately a full snapshot — name, sku,
 * barcode, price, tax — taken at the moment the item was added, so an
 * invoice stays historically correct even if the underlying catalog product
 * (name, price) changes or is removed later. {@code productId}/{@code variantId}
 * are kept only as a soft back-reference for reporting, never re-read at
 * render/download time.
 */
@Entity
@Table(name = "invoice_items")
@Data
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonIgnore
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    private ItemType itemType;

    /** Soft references — for reporting only, never dereferenced to render the invoice. */
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    // ── Snapshot fields ────────────────────────────────────────────────────
    @Column(name = "name_snapshot", nullable = false)
    private String nameSnapshot;

    @Column(name = "sku_snapshot")
    private String skuSnapshot;

    @Column(name = "barcode_snapshot")
    private String barcodeSnapshot;

    /** Catalog price at the moment this item was added — null for CUSTOM items. */
    @Column(name = "catalog_price_paise")
    private Long catalogPricePaise;

    @Column(name = "unit_price_paise", nullable = false)
    private long unitPricePaise;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "discount_paise", nullable = false)
    private long discountPaise = 0;

    @Column(name = "tax_rate", nullable = false)
    private double taxRate = 0;

    @Column(name = "tax_paise", nullable = false)
    private long taxPaise = 0;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    // ── Price-override audit trail ────────────────────────────────────────
    @Column(name = "price_override", nullable = false)
    private boolean priceOverride = false;

    @Column(name = "override_reason")
    private String overrideReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum ItemType { CATALOG, CUSTOM }
}
