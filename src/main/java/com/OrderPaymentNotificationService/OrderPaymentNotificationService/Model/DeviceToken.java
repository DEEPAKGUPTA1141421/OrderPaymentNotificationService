package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Stores FCM / APNS push notification device tokens.
 *
 * Design decisions:
 * - One user can have multiple devices (phone + tablet + web PWA).
 * - Token is unique across the entire table — if a token is re-registered
 * by a different user (shared/sold device), old binding is revoked.
 * - Tokens are invalidated (active=false) rather than deleted to maintain
 * audit trail and support token rotation analytics.
 */
@Entity
@Table(name = "device_tokens", indexes = {
        @Index(name = "idx_dt_user_id", columnList = "user_id"),
        @Index(name = "idx_dt_token_unique", columnList = "device_token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * FCM registration token (Android/Web) or APNS device token (iOS).
     * Max length per FCM spec is ~4096 chars; 512 is safe for current tokens.
     */
    @Column(name = "device_token", nullable = false, length = 512, unique = true)
    private String deviceToken;

    /**
     * Platform: ANDROID, IOS, WEB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 15)
    private Platform platform;

    /**
     * Friendly device name (optional) — e.g. "Rajan's OnePlus 12".
     * Populated by client app via User-Agent or explicit field.
     */
    @Column(name = "device_name", length = 100)
    private String deviceName;

    /**
     * App version at registration time — useful for deprecating old token formats.
     */
    @Column(name = "app_version", length = 30)
    private String appVersion;

    /**
     * Whether this token is currently active.
     * Set to false when FCM returns NotRegistered / InvalidRegistration error.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    public enum Platform {
        ANDROID, IOS, WEB
    }
}