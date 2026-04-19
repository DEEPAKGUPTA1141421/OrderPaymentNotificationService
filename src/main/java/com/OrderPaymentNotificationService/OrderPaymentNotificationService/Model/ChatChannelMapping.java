package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "chat_channel_mapping",
    indexes = {
        @Index(name = "idx_ccm_reference", columnList = "reference_id, reference_type"),
        @Index(name = "idx_ccm_status",    columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatChannelMapping {

    public enum ReferenceType {
        ORDER_CUSTOMER_SELLER,
        ORDER_CUSTOMER_RIDER,
        SUPPORT
    }

    public enum ChannelStatus {
        ACTIVE, ARCHIVED, CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private ReferenceType referenceType;

    @Column(name = "channel_url", nullable = false, length = 500)
    private String channelUrl;

    @Column(name = "channel_name", length = 255)
    private String channelName;

    @Column(name = "participant_ids", length = 1000)
    private String participantIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ChannelStatus status = ChannelStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "archive_scheduled_at")
    private Instant archiveScheduledAt;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
