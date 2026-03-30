package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class SaveUpiRequest {
    @NotBlank(message = "UPI ID is required")
    @Pattern(regexp = "^[a-zA-Z0-9._\\-]{2,256}@[a-zA-Z]{2,64}$", message = "Invalid UPI ID format. Must be in the form of user@bank")
    @Size(max = 60, message = "UPI ID too long")
    private String upiId;

    @Size(max = 80, message = "Display name max 80 chars")
    private String upiDisplayName;

    @Size(max = 50, message = "Nickname max 50 chars")
    private String nickname;

    private boolean makeDefault;
}
