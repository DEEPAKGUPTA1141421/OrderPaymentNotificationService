package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RedeemPointsResponse {
    private long pointsRedeemed;
    private BigDecimal valueInRupees; // points × rate
    private String destination;
    private String status;
    private String message;
    private UUID walletTransactionId; // set when destination=WALLET
}
