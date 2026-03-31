package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePreferenceRequest {

    @NotBlank(message = "channel is required")
    @Pattern(regexp = "^(EMAIL|SMS|PUSH|IN_APP)$", message = "channel must be one of: EMAIL, SMS, PUSH, IN_APP")
    private String channel;

    @NotNull(message = "enabled flag is required")
    private Boolean enabled;

    /**
     * Optional quiet-hours window — both must be provided together, or both null.
     * Format: HH:mm (24-hour).
     */
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietStart must be HH:mm (24h)")
    private String quietStart;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietEnd must be HH:mm (24h)")
    private String quietEnd;

    /**
     * Maximum notifications per day for this category+channel.
     * 0 = unlimited.
     */
    @Min(value = 0, message = "dailyCap must be >= 0")
    @Max(value = 100, message = "dailyCap must be <= 100")
    private Integer dailyCap;
}