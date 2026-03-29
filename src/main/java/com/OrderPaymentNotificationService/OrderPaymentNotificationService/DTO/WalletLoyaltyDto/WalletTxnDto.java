package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import lombok.Builder;

@Builder
public class WalletTxnDto {
    private UUID id;
    private String type; // CREDIT / DEBIT
    private String source;
    private BigDecimal amountRupees;
    private BigDecimal closingBalanceRupees;
    private String referenceId;
    private String description;
    private String status;
    private ZonedDateTime createdAt;
}
