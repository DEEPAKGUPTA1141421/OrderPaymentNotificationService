package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoyaltyBalanceDto {
    private UUID loyaltyAccountId;
    private long pointsBalance;
    private long lifetimeEarned;
    private long lifetimeRedeemed;
    private String tier; // SILVER / GOLD / PLATINUM
    private String tierProgress; // "4200/5000 to GOLD"
    private long pointsToNextTier;
    private BigDecimal pointsValueRupees; // 1 point = ₹0.25 or configurable
    private List<LoyaltyTxnDto> recentTransactions;
}
