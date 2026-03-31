package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Builder
public class DeviceTokenDto {
    private UUID id;
    private String platform;
    private String deviceName;
    private String appVersion;
    private boolean active;
    private ZonedDateTime createdAt;
    // Note: deviceToken is intentionally excluded from response for security
}