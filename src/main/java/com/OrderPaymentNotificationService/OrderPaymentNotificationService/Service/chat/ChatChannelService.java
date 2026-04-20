package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.ChatChannelMapping;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ChatChannelMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatChannelService {

    private final SendBirdClient sendBirdClient;
    private final ChatChannelMappingRepository channelMappingRepository;

    @Value("${sendbird.channel.archive-delay-days:7}")
    private int archiveDelayDays;

    public void createCustomerSellerChannel(UUID orderId, UUID customerId, UUID sellerId) {
        String refId = orderId.toString();
        Optional<ChatChannelMapping> existing = channelMappingRepository
            .findByReferenceIdAndReferenceType(refId, ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER);
        if (existing.isPresent()) {
            log.info("[ChatChannelService] customer-seller channel already exists for order {}", orderId);
            return;
        }

        String sbCustomerId = toSbUserId(customerId);
        String sbSellerId   = toSbUserId(sellerId);
        sendBirdClient.upsertUser(sbCustomerId, sbCustomerId);
        sendBirdClient.upsertUser(sbSellerId, sbSellerId);

        String channelName = "order_" + orderId + "_customer_seller";
        String channelUrl  = sendBirdClient.createGroupChannel(channelName, List.of(sbCustomerId, sbSellerId));

        channelMappingRepository.save(ChatChannelMapping.builder()
            .referenceId(refId)
            .referenceType(ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER)
            .channelUrl(channelUrl)
            .channelName(channelName)
            .participantIds(sbCustomerId + "," + sbSellerId)
            .build());

        log.info("[ChatChannelService] Created customer-seller channel for order {}: {}", orderId, channelUrl);
    }

    public void createCustomerRiderChannel(UUID orderId, UUID customerId, UUID riderId) {
        String refId = orderId.toString();
        Optional<ChatChannelMapping> existing = channelMappingRepository
            .findByReferenceIdAndReferenceType(refId, ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_RIDER);
        if (existing.isPresent()) {
            log.info("[ChatChannelService] customer-rider channel already exists for order {}", orderId);
            return;
        }

        String sbCustomerId = toSbUserId(customerId);
        String sbRiderId    = toSbUserId(riderId);
        sendBirdClient.upsertUser(sbCustomerId, sbCustomerId);
        sendBirdClient.upsertUser(sbRiderId, sbRiderId);

        String channelName = "order_" + orderId + "_customer_rider";
        String channelUrl  = sendBirdClient.createGroupChannel(channelName, List.of(sbCustomerId, sbRiderId));

        channelMappingRepository.save(ChatChannelMapping.builder()
            .referenceId(refId)
            .referenceType(ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_RIDER)
            .channelUrl(channelUrl)
            .channelName(channelName)
            .participantIds(sbCustomerId + "," + sbRiderId)
            .build());

        log.info("[ChatChannelService] Created customer-rider channel for order {}: {}", orderId, channelUrl);
    }

    public String createSupportChannel(String ticketId, UUID customerId, UUID agentId) {
        String sbCustomerId = toSbUserId(customerId);
        sendBirdClient.upsertUser(sbCustomerId, sbCustomerId);

        List<String> participants;
        String participantIds;
        if (agentId != null) {
            String sbAgentId = toSbUserId(agentId);
            sendBirdClient.upsertUser(sbAgentId, sbAgentId);
            participants  = List.of(sbCustomerId, sbAgentId);
            participantIds = sbCustomerId + "," + sbAgentId;
        } else {
            participants  = List.of(sbCustomerId);
            participantIds = sbCustomerId;
        }

        String channelName = "support_" + ticketId + "_customer_agent";
        String channelUrl  = sendBirdClient.createGroupChannel(channelName, participants);

        channelMappingRepository.save(ChatChannelMapping.builder()
            .referenceId(ticketId)
            .referenceType(ChatChannelMapping.ReferenceType.SUPPORT)
            .channelUrl(channelUrl)
            .channelName(channelName)
            .participantIds(participantIds)
            .build());

        log.info("[ChatChannelService] Created support channel for ticket {}: {}", ticketId, channelUrl);
        return channelUrl;
    }

    public void handleOrderDelivered(UUID orderId) {
        // Close the customer-rider channel immediately (no more updates needed)
        channelMappingRepository
            .findByReferenceIdAndReferenceType(orderId.toString(), ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_RIDER)
            .ifPresent(mapping -> {
                try {
                    sendBirdClient.freezeChannel(mapping.getChannelUrl());
                    mapping.setStatus(ChatChannelMapping.ChannelStatus.CLOSED);
                    mapping.setArchivedAt(Instant.now());
                    channelMappingRepository.save(mapping);
                    log.info("[ChatChannelService] Closed rider channel for order {}", orderId);
                } catch (Exception e) {
                    log.error("[ChatChannelService] Failed to close rider channel for order {}: {}", orderId, e.getMessage());
                }
            });

        // Schedule customer-seller channel archival after archiveDelayDays days
        channelMappingRepository
            .findByReferenceIdAndReferenceType(orderId.toString(), ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER)
            .ifPresent(mapping -> {
                mapping.setArchiveScheduledAt(Instant.now().plusSeconds((long) archiveDelayDays * 86400));
                channelMappingRepository.save(mapping);
                log.info("[ChatChannelService] Scheduled archival for order {} customer-seller channel at {}",
                    orderId, mapping.getArchiveScheduledAt());
            });
    }

    public void runScheduledArchival() {
        List<ChatChannelMapping> due = channelMappingRepository
            .findByStatusAndArchiveScheduledAtBefore(ChatChannelMapping.ChannelStatus.ACTIVE, Instant.now());

        for (ChatChannelMapping mapping : due) {
            try {
                sendBirdClient.archiveChannel(mapping.getChannelUrl());
                mapping.setStatus(ChatChannelMapping.ChannelStatus.ARCHIVED);
                mapping.setArchivedAt(Instant.now());
                channelMappingRepository.save(mapping);
                log.info("[ChatChannelService] Archived channel {}", mapping.getChannelUrl());
            } catch (Exception e) {
                log.error("[ChatChannelService] Failed to archive channel {}: {}", mapping.getChannelUrl(), e.getMessage());
            }
        }
    }

    private String toSbUserId(UUID userId) {
        return "usr_" + userId.toString().replace("-", "");
    }
}
