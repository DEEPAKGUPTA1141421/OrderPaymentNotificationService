package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Persists in-app notifications for the user's notification feed.
 *
 * Lifecycle:
 * UNREAD → READ (via PATCH /{id}/read or /read-all)
 * UNREAD/READ → soft-deleted (DELETE /{id})
 *
 * Indexing strategy:
 * - (userId, isRead, createdAt DESC) — primary feed query
 * - (userId, category) — filtered feed (e.g. "show only ORDER_UPDATES")
 */
@Entity
@Table(name = "in_app_notifications", indexes = {
        @Index(name = "idx_ian_user_feed", columnList = "user_id, is_read, created_at"),
        @Index(name = "idx_ian_user_category", columnList = "user_id, category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Notification category — mirrors NotificationPreference.NotificationCategory.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private NotificationPreference.NotificationCategory category;

    /**
     * Short title shown in the notification badge/list.
     */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /**
     * Full notification body.
     */
    @Column(name = "body", nullable = false, length = 500)
    private String body;

    /**
     * Deep-link or web URL the notification should navigate to on tap.
     * e.g. /orders/{orderId}
     */
    @Column(name = "action_url", length = 255)
    private String actionUrl;

    /**
     * Optional icon/image URL (CDN link to category-specific icon).
     */
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    /**
     * Reference entity (orderId, paymentId, etc.) for correlation.
     */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    /**
     * Whether the user has read this notification.
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    /**
     * Soft-delete flag — deleted notifications are excluded from feed queries.
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "read_at")
    private ZonedDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
}
