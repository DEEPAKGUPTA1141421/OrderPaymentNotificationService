package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WalletBalanceDto {
    private UUID walletId;
    private BigDecimal balanceRupees; // human-readable
    private long balancePaise; // raw amount
    private boolean frozen;
    private String frozenReason;
    private List<WalletTxnDto> recentTransactions;
}
