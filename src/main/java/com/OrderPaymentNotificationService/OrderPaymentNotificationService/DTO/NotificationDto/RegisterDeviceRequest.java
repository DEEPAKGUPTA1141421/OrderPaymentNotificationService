package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDeviceRequest {

    @NotBlank(message = "deviceToken is required")
    @Size(min = 10, max = 512, message = "deviceToken must be between 10 and 512 characters")
    private String deviceToken;

    @NotBlank(message = "platform is required")
    @Pattern(regexp = "^(ANDROID|IOS|WEB)$", message = "platform must be one of: ANDROID, IOS, WEB")
    private String platform;

    @Size(max = 100, message = "deviceName max 100 chars")
    private String deviceName;

    @Size(max = 30, message = "appVersion max 30 chars")
    private String appVersion;
}