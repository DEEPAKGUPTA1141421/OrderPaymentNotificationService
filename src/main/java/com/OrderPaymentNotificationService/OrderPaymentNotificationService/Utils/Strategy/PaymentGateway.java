package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy;

import java.util.Map;
import java.util.UUID;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CreateOrderDto;

public interface PaymentGateway {
    ApiResponse<Object> createOrder(CreateOrderDto dto);

    ApiResponse<Object> validatePayment(UUID transactionId);

    ApiResponse<Object> refundPayment(UUID transactionId, String amount);

    ApiResponse<Object> handleWebhook(Map<String, Object> payload);
}
