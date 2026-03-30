package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import jakarta.validation.constraints.*;
import lombok.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SavedPaymentMethod;

@Getter
@Setter
public class SaveCardRequest {

    @NotBlank(message = "Gateway token is required")
    @Size(max = 120, message = "Token too long")
    private String gatewayToken; // Razorpay / Stripe card vault token

    @NotBlank(message = "Card last 4 digits required")
    @Pattern(regexp = "^[0-9]{4}$", message = "cardLast4 must be exactly 4 digits")
    private String cardLast4;

    @NotBlank(message = "Card brand is required")
    @Pattern(regexp = "^(VISA|MASTERCARD|RUPAY|AMEX|DINERS|MAESTRO|DISCOVER)$", message = "Unsupported card brand")
    private String cardBrand;

    @NotBlank(message = "Card holder name is required")
    @Size(min = 2, max = 100, message = "Card holder name must be 2–100 characters")
    @Pattern(regexp = "^[a-zA-Z .'-]+$", message = "Card holder name contains invalid characters")
    private String cardHolderName;

    /**
     * Expiry in MM/YYYY format (only month/year — no CVV stored ever).
     */
    @NotBlank(message = "Card expiry is required")
    @Pattern(regexp = "^(0[1-9]|1[0-2])/20[2-9][0-9]$", message = "Expiry must be MM/YYYY and in the future")
    private String cardExpiry;

    @NotNull(message = "Card type is required")
    private SavedPaymentMethod.CardType cardType;

    @Size(max = 50, message = "Nickname max 50 chars")
    private String nickname;

    private boolean makeDefault;

    @NotBlank(message = "Gateway name is required")
    @Pattern(regexp = "^(razorpay|stripe|payu|cashfree|ccavenue)$", message = "Unsupported payment gateway")
    private String gateway;
}
