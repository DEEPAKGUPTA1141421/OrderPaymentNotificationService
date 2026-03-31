package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Builder
public class InAppNotificationDto {
    private UUID id;
    private String category;
    private String title;
    private String body;
    private String actionUrl;
    private String imageUrl;
    private String referenceId;
    private boolean read;
    private ZonedDateTime readAt;
    private ZonedDateTime createdAt;
}