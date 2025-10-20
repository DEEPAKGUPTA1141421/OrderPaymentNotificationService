package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import java.util.Map;
import java.util.UUID;

public interface PhonePeService {
    Map<String, Object> createOrder(String merchantOrderId, String amount, String idempotencyKey);

    Map<String, Object> checkOrderStatus(String merchantOrderId);

    public Map<String, Object> refundPayment(String merchantOrderId, long amount);
}
