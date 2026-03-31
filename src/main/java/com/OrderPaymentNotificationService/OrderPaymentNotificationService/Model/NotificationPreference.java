package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Stores per-user, per-channel, per-category notification preferences.
 *
 * Design goals:
 * - One row per (userId, category, channel) triple → fully granular control.
 * - Unique constraint prevents duplicates and makes UPSERT safe.
 * - Soft-enable/disable at both category and channel level.
 */
@Entity
@Table(name = "notification_preferences", indexes = {
        @Index(name = "idx_np_user_id", columnList = "user_id"),
        @Index(name = "idx_np_user_category", columnList = "user_id, category")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_np_user_category_channel", columnNames = { "user_id", "category", "channel" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Notification category e.g. ORDER_UPDATES, PROMOTIONS, WALLET, etc.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private NotificationCategory category;

    /**
     * Delivery channel: EMAIL, SMS, PUSH, IN_APP.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /**
     * Whether the user has enabled this category+channel combination.
     * Defaults to true for transactional categories, false for marketing.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Quiet-hours: don't send between these times (HH:mm, 24h, user's local tz).
     * Null = no quiet hours enforced.
     */
    @Column(name = "quiet_start", length = 5)
    private String quietStart; // "22:00"

    @Column(name = "quiet_end", length = 5)
    private String quietEnd; // "08:00"

    /**
     * Frequency cap: maximum number of notifications per day for this
     * category+channel.
     * 0 = unlimited.
     */
    @Column(name = "daily_cap")
    @Builder.Default
    private int dailyCap = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    // ── Enums ────────────────────────────────────────────────────────────────

    public enum NotificationCategory {
        ORDER_UPDATES, // Order placed, shipped, delivered, cancelled
        PAYMENT_UPDATES, // Payment success/failure, refund initiated
        WALLET_UPDATES, // Top-up, debit, low-balance alerts
        LOYALTY_UPDATES, // Points earned, tier upgrade, expiry warnings
        PROMOTIONS, // Deals, coupons, flash sales
        PRODUCT_UPDATES, // Price drop, back-in-stock alerts
        ACCOUNT_SECURITY, // Login OTP, password change, suspicious login
        REVIEW_REMINDERS, // Remind to review purchased product
        SYSTEM_ALERTS // Downtime notices, policy updates
    }

    public enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH, // Firebase / APNS push notification
        IN_APP // In-app notification feed
    }
}