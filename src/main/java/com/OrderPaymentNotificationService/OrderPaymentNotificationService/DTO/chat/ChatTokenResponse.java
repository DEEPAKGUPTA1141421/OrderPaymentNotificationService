package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatTokenResponse {
    private String sendbirdUserId;
    private String sessionToken;
    private long expiresAt;
}
