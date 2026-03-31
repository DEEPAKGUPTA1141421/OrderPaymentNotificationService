package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkUpdatePreferenceRequest {

    @NotNull(message = "preferences list is required")
    @Size(min = 1, max = 40, message = "Must provide between 1 and 40 preference entries")
    @Valid
    private List<PreferenceEntry> preferences;

    @Getter
    @Setter
    public static class PreferenceEntry {

        @NotBlank(message = "category is required")
        @Pattern(regexp = "^(ORDER_UPDATES|PAYMENT_UPDATES|WALLET_UPDATES|LOYALTY_UPDATES|"
                + "PROMOTIONS|PRODUCT_UPDATES|ACCOUNT_SECURITY|REVIEW_REMINDERS|SYSTEM_ALERTS)$", message = "Invalid notification category")
        private String category;

        @NotBlank(message = "channel is required")
        @Pattern(regexp = "^(EMAIL|SMS|PUSH|IN_APP)$", message = "channel must be one of: EMAIL, SMS, PUSH, IN_APP")
        private String channel;

        @NotNull(message = "enabled flag is required")
        private Boolean enabled;

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietStart must be HH:mm (24h)")
        private String quietStart;

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietEnd must be HH:mm (24h)")
        private String quietEnd;

        @Min(0)
        @Max(100)
        private Integer dailyCap;
    }
}