package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AddMoneyResponseDto {
    private UUID walletTransactionId;
    private String gatewayOrderId; // from payment gateway
    private String gatewayPaymentUrl; // redirect / UPI deep-link
    private BigDecimal amountRupees;
    private String status; // INITIATED / SUCCESS / FAILED
    private String message;
}
