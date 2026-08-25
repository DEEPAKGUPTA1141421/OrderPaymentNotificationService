package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import java.util.Map;

public interface RazorpayService {
    Map<String, Object> createOrder(String merchantOrderId, String amount, String idempotencyKey);

    Map<String, Object> checkOrderStatus(String razorpayOrderId);

    Map<String, Object> refundPayment(String razorpayPaymentId, long amountPaise);
}
