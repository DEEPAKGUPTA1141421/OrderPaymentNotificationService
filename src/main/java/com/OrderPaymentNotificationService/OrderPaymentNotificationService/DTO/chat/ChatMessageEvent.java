package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat;

import lombok.Builder;
import lombok.Data;

/**
 * Published to Kafka topic chat.message.received when a SendBird webhook fires.
 * Notification service consumes this to send FCM push to offline users.
 */
@Data
@Builder
public class ChatMessageEvent {
    private String channelUrl;
    private String senderId;
    private long messageId;
    private String messageText;
    private long sentAt;
}
