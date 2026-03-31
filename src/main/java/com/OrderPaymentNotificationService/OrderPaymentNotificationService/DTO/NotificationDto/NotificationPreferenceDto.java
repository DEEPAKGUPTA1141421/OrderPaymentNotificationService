package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationPreferenceDto {
    private UUID id;
    private String category;
    private String channel;
    private boolean enabled;
    private String quietStart;
    private String quietEnd;
    private int dailyCap;
}