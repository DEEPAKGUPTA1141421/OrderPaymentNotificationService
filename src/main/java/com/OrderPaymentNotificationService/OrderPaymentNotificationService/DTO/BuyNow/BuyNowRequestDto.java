package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.BuyNow;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BuyNowRequestDto(

        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Variant ID is required")
        UUID variantId,

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 10, message = "Maximum 10 units per Buy Now order")
        int quantity,

        @NotNull(message = "Delivery address is required")
        UUID deliveryAddressId
) {}
