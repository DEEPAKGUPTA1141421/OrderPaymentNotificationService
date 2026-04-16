package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.DateTimeUtil;

@Entity
@Table(name = "loyalty_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "points_balance", nullable = false)
    @Builder.Default
    private long pointsBalance = 0L;

    @Column(name = "lifetime_earned", nullable = false)
    @Builder.Default
    private long lifetimeEarned = 0L;

    @Column(name = "lifetime_redeemed", nullable = false)
    @Builder.Default
    private long lifetimeRedeemed = 0L;

    /**
     * Tier based on lifetime earned points:
     * SILVER → 0–4999
     * GOLD → 5000–19999
     * PLATINUM→ 20000+
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 15)
    @Builder.Default
    private LoyaltyTier tier = LoyaltyTier.SILVER;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = DateTimeUtil.nowIst();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = DateTimeUtil.nowIst();

    public void recalculateTier() {
        if (this.lifetimeEarned >= 20_000) {
            this.tier = LoyaltyTier.PLATINUM;
        } else if (this.lifetimeEarned >= 5_000) {
            this.tier = LoyaltyTier.GOLD;
        } else {
            this.tier = LoyaltyTier.SILVER;
        }
    }

    public void earnPoints(long pts) {
        this.pointsBalance += pts;
        this.lifetimeEarned += pts;
        recalculateTier();
    }

    public void redeemPoints(long pts) {
        if (pts > this.pointsBalance)
            throw new IllegalStateException("Insufficient loyalty points");
        this.pointsBalance -= pts;
        this.lifetimeRedeemed += pts;
    }

    public enum LoyaltyTier {
        SILVER, GOLD, PLATINUM
    }
}
