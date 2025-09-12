package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.CartResponseDto.CartItemDto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponseDto {
    private UUID cartId;
    private UUID userId;
    private String status;
    private List<CartItemDto> items;

    private double totalAmount;
    private double totalDiscount;
    private double serviceCharge;
    private double deliveryCharge;
    private double gstCharge;
    private double grandTotal;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemDto {
        private UUID id;
        private UUID productId;
        private UUID shopId;
        private int quantity;
        private double price;
    }
}
// jhuk hhyhhujjuhj nnu jijiojji iuhuih hhiu