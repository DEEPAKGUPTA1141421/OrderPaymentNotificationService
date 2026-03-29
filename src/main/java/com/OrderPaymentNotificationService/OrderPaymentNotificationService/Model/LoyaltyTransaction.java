package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "loyalty_transactions", indexes = {
        @Index(name = "idx_lt_user_id", columnList = "user_id"),
        @Index(name = "idx_lt_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loyalty_account_id", nullable = false)
    private LoyaltyAccount loyaltyAccount;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private TxType type; // EARN / REDEEM / EXPIRE / REVERSE

    @Column(name = "points", nullable = false)
    private long points; // always positive; type determines direction

    @Column(name = "closing_balance", nullable = false)
    private long closingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private TxSource source;

    /** orderId, walletTxnId, adminRef … */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "description", length = 255)
    private String description;

    /** When this earn batch expires (null = never) */
    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;
    // inside LoyaltyTransaction class:
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    public enum TxType {
        EARN, REDEEM, EXPIRE, REVERSE
    }

    public enum TxSource {
        ORDER_PURCHASE, // earned on completed order
        WELCOME_BONUS, // first-time user
        REFERRAL, // referral reward
        REDEMPTION, // points redeemed (debit)
        ADMIN_ADJUST, // manual by admin
        EXPIRY // expired points (debit)
    }
}
