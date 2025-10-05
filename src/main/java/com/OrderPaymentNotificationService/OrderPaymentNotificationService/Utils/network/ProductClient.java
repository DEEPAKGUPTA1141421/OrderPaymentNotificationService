package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.network;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.CartResponseDto;

@FeignClient(name = "productclient", url = "${feign.client.productclient.url}")
public interface ProductClient {
    @GetMapping("/api/v1/cart/get-cart")
    ApiResponse<CartResponseDto> getCart(@RequestHeader("X-User-Id") UUID userId);

}

// hytyu tgutu
