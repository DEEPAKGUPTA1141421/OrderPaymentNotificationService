package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SendBirdWebhookPayload {

    private String category;

    @JsonProperty("channel")
    private ChannelInfo channel;

    @JsonProperty("sender")
    private SenderInfo sender;

    @JsonProperty("payload")
    private MessagePayload payload;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChannelInfo {
        @JsonProperty("channel_url")
        private String channelUrl;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SenderInfo {
        @JsonProperty("user_id")
        private String userId;
        private String nickname;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessagePayload {
        @JsonProperty("message_id")
        private long messageId;
        private String message;
        @JsonProperty("created_at")
        private long createdAt;
    }
}
