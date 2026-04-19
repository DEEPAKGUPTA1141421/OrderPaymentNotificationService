-- Chat channel mapping table: links domain IDs (orderId, ticketId) to SendBird channelUrls.
-- V2: existing schema managed by Hibernate ddl-auto=update; Flyway baseline set to V1.

CREATE TABLE IF NOT EXISTS chat_channel_mapping (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id         VARCHAR(255) NOT NULL,
    reference_type       VARCHAR(50)  NOT NULL,
    channel_url          VARCHAR(500) NOT NULL,
    channel_name         VARCHAR(255),
    participant_ids      VARCHAR(1000),
    status               VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archive_scheduled_at TIMESTAMPTZ,
    archived_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ccm_reference ON chat_channel_mapping (reference_id, reference_type);
CREATE INDEX IF NOT EXISTS idx_ccm_status    ON chat_channel_mapping (status);
