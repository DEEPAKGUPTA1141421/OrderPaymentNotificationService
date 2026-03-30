package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import java.time.ZonedDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoyaltyTxnDto {
    private UUID id;
    private String type; // EARN / REDEEM / EXPIRE / REVERSE
    private String source;
    private long points;
    private long closingBalance;
    private String referenceId;
    private String description;
    private ZonedDateTime expiresAt;
    private ZonedDateTime createdAt;
}
