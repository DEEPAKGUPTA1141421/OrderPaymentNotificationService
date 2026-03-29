package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wt_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_wt_user_id", columnList = "user_id"),
        @Index(name = "idx_wt_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private TransactionSource source; // what caused the transaction

    @Column(name = "amount_paise", nullable = false, precision = 18, scale = 0)
    private BigDecimal amountPaise;

    /** Balance AFTER this transaction (snapshot for audit trail) */
    @Column(name = "closing_balance_paise", nullable = false, precision = 18, scale = 0)
    private BigDecimal closingBalancePaise;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.SUCCESS;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    public enum TransactionType {
        CREDIT, DEBIT
    }

    public enum TransactionSource {
        TOP_UP, // user added money via payment gateway
        ORDER_PAYMENT, // wallet used to pay for an order
        ORDER_REFUND, // refund credited back to wallet
        LOYALTY_REDEEM, // loyalty points redeemed into wallet
        ADMIN_CREDIT, // manual credit by admin (compensation, cashback)
        ADMIN_DEBIT, // manual debit by admin
        REVERSAL // chargeback / reversal
    }

    public enum TransactionStatus {
        SUCCESS, PENDING, FAILED, REVERSED
    }
}
