package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto;

import java.util.UUID;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RedeemPointsRequest {
    @NotNull(message = "Points to redeem is required")
    @Min(value = 100, message = "Minimum redemption is 100 points")
    @Max(value = 10_000, message = "Maximum redemption per request is 10,000 points")
    private Long points;

    /**
     * Where to credit the redeemed value:
     * WALLET → convert points to wallet balance
     * ORDER → apply discount on a specific order (provide orderId)
     */
    @NotBlank(message = "Destination is required")
    @Pattern(regexp = "^(WALLET|ORDER)$", message = "destination must be WALLET or ORDER")
    private String destination;

    /** Required when destination=ORDER */
    private UUID orderId;

    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 100)
    private String idempotencyKey;
}
