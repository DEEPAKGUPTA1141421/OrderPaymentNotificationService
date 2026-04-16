package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.network;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.CartResponseDto;

@FeignClient(name = "productclient", url = "${feign.client.productclient.url}")
public interface ProductClient {

    /**
     * Internal service-to-service call to fetch a user's active cart.
     * Protected by the X-Internal-Api-Key header — not exposed to end users.
     *
     * Endpoint in cart service:
     *   GET /internal/v1/cart/{userId}
     *   Filter: InternalApiKeyFilter validates X-Internal-Api-Key header.
     */
    @GetMapping("/internal/v1/cart/{userId}")
    ApiResponse<CartResponseDto> getCartInternal(
            @PathVariable("userId") UUID userId,
            @RequestHeader("X-Internal-Api-Key") String internalApiKey
    );
}
