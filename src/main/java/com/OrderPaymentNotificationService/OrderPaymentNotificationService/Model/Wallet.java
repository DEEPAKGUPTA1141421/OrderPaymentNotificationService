package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "balance_paise", nullable = false, precision = 18, scale = 0)
    @Builder.Default
    private BigDecimal balancePaise = BigDecimal.ZERO;

    @Column(name = "is_frozen", nullable = false)
    @Builder.Default
    private boolean frozen = false;

    @Column(name = "frozen_reason")
    private String frozenReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    @Transient
    public BigDecimal getBalanceInRupees() {
        return balancePaise.divide(BigDecimal.valueOf(100));
    }

    public void credit(BigDecimal paise) {
        if (paise.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Credit amount must be positive");
        this.balancePaise = this.balancePaise.add(paise);
    }

    public void debit(BigDecimal paise) {
        if (paise.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Debit amount must be positive");
        if (this.balancePaise.compareTo(paise) < 0)
            throw new IllegalStateException("Insufficient wallet balance");
        this.balancePaise = this.balancePaise.subtract(paise);
    }
}
