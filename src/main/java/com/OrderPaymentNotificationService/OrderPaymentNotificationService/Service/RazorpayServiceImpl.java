package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;

import lombok.extern.slf4j.Slf4j;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class RazorpayServiceImpl implements RazorpayService {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Override
    public Map<String, Object> createOrder(String merchantOrderId, String amount, String idempotencyKey) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            long paymentAmount = (long) (Double.parseDouble(amount) * 100);

            JSONObject notes = new JSONObject();
            notes.put("merchantOrderId", merchantOrderId);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", paymentAmount);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", merchantOrderId);
            orderRequest.put("notes", notes);

            Order order = client.orders.create(orderRequest);

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.get("id").toString());
            result.put("keyId", keyId);
            result.put("amount", paymentAmount);
            result.put("currency", "INR");
            result.put("status", order.get("status").toString());
            return result;
        } catch (Exception e) {
            log.error("Razorpay order creation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> checkOrderStatus(String razorpayOrderId) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            Order order = client.orders.fetch(razorpayOrderId);

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.get("id").toString());
            result.put("status", order.get("status").toString());
            result.put("amountPaid", order.get("amount_paid"));
            return result;
        } catch (Exception e) {
            log.error("Razorpay order status fetch failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch Razorpay order status: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> refundPayment(String razorpayPaymentId, long amountPaise) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amountPaise);

            Refund refund = client.payments.refund(razorpayPaymentId, refundRequest);

            Map<String, Object> result = new HashMap<>();
            result.put("refundId", refund.get("id").toString());
            result.put("status", refund.get("status").toString());
            return result;
        } catch (Exception e) {
            log.error("Razorpay refund failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to refund Razorpay payment: " + e.getMessage());
        }
    }
}
