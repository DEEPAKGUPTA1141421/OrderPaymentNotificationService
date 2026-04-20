package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.ChatChannelMapping;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ChatChannelMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatChannelServiceTest {

    @Mock private SendBirdClient sendBirdClient;
    @Mock private ChatChannelMappingRepository channelMappingRepository;

    @InjectMocks
    private ChatChannelService chatChannelService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(chatChannelService, "archiveDelayDays", 7);
    }

    @Test
    void createCustomerSellerChannel_createsChannelAndPersistsMapping() {
        UUID orderId    = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sellerId   = UUID.randomUUID();

        when(channelMappingRepository.findByReferenceIdAndReferenceType(
            orderId.toString(), ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER))
            .thenReturn(Optional.empty());
        when(sendBirdClient.createGroupChannel(anyString(), anyList())).thenReturn("channel_abc123");
        when(channelMappingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        chatChannelService.createCustomerSellerChannel(orderId, customerId, sellerId);

        verify(sendBirdClient, times(2)).upsertUser(anyString(), anyString());
        verify(sendBirdClient).createGroupChannel(
            contains("customer_seller"), anyList());

        ArgumentCaptor<ChatChannelMapping> captor = ArgumentCaptor.forClass(ChatChannelMapping.class);
        verify(channelMappingRepository).save(captor.capture());
        ChatChannelMapping saved = captor.getValue();
        assertThat(saved.getReferenceId()).isEqualTo(orderId.toString());
        assertThat(saved.getReferenceType()).isEqualTo(ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER);
        assertThat(saved.getChannelUrl()).isEqualTo("channel_abc123");
        assertThat(saved.getStatus()).isEqualTo(ChatChannelMapping.ChannelStatus.ACTIVE);
    }

    @Test
    void createCustomerSellerChannel_isIdempotent_whenChannelAlreadyExists() {
        UUID orderId = UUID.randomUUID();
        ChatChannelMapping existing = ChatChannelMapping.builder()
            .referenceId(orderId.toString())
            .referenceType(ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER)
            .channelUrl("channel_existing")
            .build();

        when(channelMappingRepository.findByReferenceIdAndReferenceType(
            orderId.toString(), ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER))
            .thenReturn(Optional.of(existing));

        chatChannelService.createCustomerSellerChannel(orderId, UUID.randomUUID(), UUID.randomUUID());

        verifyNoInteractions(sendBirdClient);
        verify(channelMappingRepository, never()).save(any());
    }

    @Test
    void handleOrderDelivered_freezesRiderChannelAndSchedulesSellerArchival() {
        UUID orderId = UUID.randomUUID();

        ChatChannelMapping riderMapping = ChatChannelMapping.builder()
            .referenceId(orderId.toString())
            .referenceType(ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_RIDER)
            .channelUrl("channel_rider")
            .status(ChatChannelMapping.ChannelStatus.ACTIVE)
            .build();

        ChatChannelMapping sellerMapping = ChatChannelMapping.builder()
            .referenceId(orderId.toString())
            .referenceType(ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER)
            .channelUrl("channel_seller")
            .status(ChatChannelMapping.ChannelStatus.ACTIVE)
            .build();

        when(channelMappingRepository.findByReferenceIdAndReferenceType(
            orderId.toString(), ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_RIDER))
            .thenReturn(Optional.of(riderMapping));
        when(channelMappingRepository.findByReferenceIdAndReferenceType(
            orderId.toString(), ChatChannelMapping.ReferenceType.ORDER_CUSTOMER_SELLER))
            .thenReturn(Optional.of(sellerMapping));

        chatChannelService.handleOrderDelivered(orderId);

        verify(sendBirdClient).freezeChannel("channel_rider");
        assertThat(riderMapping.getStatus()).isEqualTo(ChatChannelMapping.ChannelStatus.CLOSED);
        assertThat(riderMapping.getArchivedAt()).isNotNull();
        assertThat(sellerMapping.getArchiveScheduledAt())
            .isAfter(Instant.now().plusSeconds(6 * 86400));
        verify(channelMappingRepository, times(2)).save(any());
    }

    @Test
    void runScheduledArchival_archivesDueChannels() {
        ChatChannelMapping dueChannel = ChatChannelMapping.builder()
            .channelUrl("channel_due")
            .status(ChatChannelMapping.ChannelStatus.ACTIVE)
            .archiveScheduledAt(Instant.now().minusSeconds(3600))
            .build();

        when(channelMappingRepository.findByStatusAndArchiveScheduledAtBefore(
            eq(ChatChannelMapping.ChannelStatus.ACTIVE), any(Instant.class)))
            .thenReturn(List.of(dueChannel));

        chatChannelService.runScheduledArchival();

        verify(sendBirdClient).archiveChannel("channel_due");
        assertThat(dueChannel.getStatus()).isEqualTo(ChatChannelMapping.ChannelStatus.ARCHIVED);
        assertThat(dueChannel.getArchivedAt()).isNotNull();
        verify(channelMappingRepository).save(dueChannel);
    }
}
