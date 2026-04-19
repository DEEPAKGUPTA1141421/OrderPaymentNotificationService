package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupportChannelResponse {
    private String channelUrl;
    private String ticketId;
}
