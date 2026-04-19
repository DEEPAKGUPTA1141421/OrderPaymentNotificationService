package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.ChatChannelMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatChannelMappingRepository extends JpaRepository<ChatChannelMapping, UUID> {

    Optional<ChatChannelMapping> findByReferenceIdAndReferenceType(
        String referenceId, ChatChannelMapping.ReferenceType referenceType);

    List<ChatChannelMapping> findByStatusAndArchiveScheduledAtBefore(
        ChatChannelMapping.ChannelStatus status, Instant before);
}
