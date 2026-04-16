package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.twilio.twiml.voice.Prompt.CardType;

import java.time.ZonedDateTime;
import java.util.UUID;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.DateTimeUtil;

@Entity
@Table(name = "saved_payment_methods", indexes = {
        @Index(name = "idx_spm_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedPaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 10)
    private MethodType methodType;

    // ── CARD fields (encrypted at rest — store only masked / token) ───────────

    /**
     * Payment gateway token (e.g. Razorpay card_id / Stripe payment-method-id).
     * NEVER store raw card numbers. This is the vault token.
     */
    @Column(name = "gateway_token", length = 120)
    private String gatewayToken;

    /** Last 4 digits of card — safe to store for display */
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_brand", length = 20)
    private String cardBrand; // VISA, MASTERCARD, RUPAY, AMEX …

    @Column(name = "card_holder_name", length = 100)
    private String cardHolderName;

    /**
     * MM/YYYY — stored only for display; gateway handles real expiry enforcement
     */
    @Column(name = "card_expiry", length = 7)
    private String cardExpiry;
    // "08/2028"

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", length = 10)
    private CardType cardType; // CREDIT / DEBIT / PREPAID

    /** e.g. user@okaxis — validated via regex before saving */
    @Column(name = "upi_id", length = 60)
    private String upiId;

    @Column(name = "upi_display_name", length = 80)
    private String upiDisplayName; // bank name or VPA alias

    @Column(name = "nickname", length = 50)
    private String nickname; // user-given label: "My SBI Card"

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Which gateway this token belongs to (razorpay, stripe, payu …).
     * Needed when charging the token.
     */
    @Column(name = "gateway", length = 30)
    private String gateway;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = DateTimeUtil.nowIst();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = DateTimeUtil.nowIst();

    // ── Enums ────────────────────────────────────────────────────────────────

    public enum MethodType {
        CARD, UPI
    }

    public enum CardType {
        CREDIT, DEBIT, PREPAID
    }
}
