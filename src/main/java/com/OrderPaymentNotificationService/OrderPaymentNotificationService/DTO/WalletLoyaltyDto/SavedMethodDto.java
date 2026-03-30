package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import java.time.ZonedDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SavedMethodDto {
    private UUID id;
    private String methodType; // CARD / UPI
    private String cardLast4;
    private String cardBrand;
    private String cardHolderName;
    private String cardExpiry;
    private String cardType; // CREDIT / DEBIT / PREPAID
    private String upiId;
    private String upiDisplayName;
    private String nickname;
    private boolean isDefault;
    private ZonedDateTime createdAt;
}
