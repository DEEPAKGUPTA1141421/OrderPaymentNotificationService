package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CreateOrderDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.RedisLockService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy.PaymentGateway;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy.PaymentGatewayFactory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentGatewayFactory gatewayFactory;
    private final RedisLockService redisLockService;

    @PostMapping
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderDto dto) {
        System.out.println("gateway" + dto.gateway());
        PaymentGateway paymentGateway = gatewayFactory.getGateway(dto.gateway());

        String lockKey = "lock:payment:" + dto.bookingId();
        try {
            // ✅ Prevent duplicate payment requests
            // if (!redisLockService.acquireLock(lockKey, dto.userId(), dto.bookingId(), 1,
            // 2)) {
            // return ResponseEntity.status(409).body("Payment already in progress for this
            // booking.");
            // }

            // ✅ Idempotency check
            // if (redisLockService.isLocked("idempotent:", dto.bookingId(),
            // UUID.fromString(lockKey)dto.idempotencyKey())) {
            // return ResponseEntity.ok(Map.of("status", "IGNORED", "reason", "Idempotent
            // request"));
            // }

            redisLockService.acquireLock("idempotent:", dto.userId(), dto.bookingId(), 1, 5);

            ApiResponse<Object> response = paymentGateway.createOrder(dto);
            return ResponseEntity.ok(response);
        } finally {
            redisLockService.releaseLock("lock:payment:", dto.userId(), dto.bookingId());
        }
    }

    @GetMapping("/validate-payment")
    public ResponseEntity<?> refundPayment(@RequestParam UUID merchantOrderId, @RequestParam String gateway) {
        PaymentGateway paymentGateway = gatewayFactory.getGateway(gateway);
        ApiResponse<Object> response = paymentGateway.validatePayment(merchantOrderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refund")

    public ResponseEntity<?> refundPayment(
            @RequestParam String gateway,
            @RequestParam UUID transactionId,
            @RequestParam String amount,
            @RequestParam UUID userId) {

        PaymentGateway paymentGateway = gatewayFactory.getGateway(gateway);
        String lockKey = "lock:refund:" + transactionId;

        try {
            // ✅ Prevent multiple refund requests
            if (!redisLockService.acquireLock(lockKey, userId, UUID.randomUUID(), 1, 2)) {
                return ResponseEntity.status(409).body("Refund already in progress for this transaction.");
            }

            ApiResponse<Object> response = paymentGateway.refundPayment(transactionId, amount);
            return ResponseEntity.ok(response);
        } finally {
            redisLockService.releaseLock("lock:refund:", userId, UUID.randomUUID());
        }
    }

    // ✅ Webhook with Lock
    @PostMapping("/webhook/{gateway}")
    public ResponseEntity<String> handleWebhook(
            @PathVariable String gateway,
            @RequestBody Map<String, Object> payload) {

        PaymentGateway paymentGateway = gatewayFactory.getGateway(gateway);

        String transactionId = String.valueOf(payload.get("transactionId"));
        String lockKey = "lock:webhook:" + transactionId;

        try {
            // ✅ Prevent double webhook processing
            int maxRetries = 3;
            int attempt = 0;

            while (!redisLockService.acquireLock("lock:webhook:", UUID.randomUUID(),
                    UUID.nameUUIDFromBytes(transactionId.getBytes()), 1, 1)) {
                if (attempt++ >= maxRetries) {
                    return ResponseEntity.status(429).body("Too many webhook hits. Try later.");
                }
                Thread.sleep(1000); // wait till previous finishes
            }

            paymentGateway.handleWebhook(payload);
            return ResponseEntity.ok("Webhook processed successfully");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().body("Interrupted while waiting for lock");
        } finally {
            redisLockService.releaseLock("lock:webhook:", UUID.randomUUID(),
                    UUID.nameUUIDFromBytes(transactionId.getBytes()));
        }
    }
}

// huhuijjkhujijkmkjhio iuuiihhb huhuhuuhjnj
// gyyhiuhj hiyuio huiuuuijuhhuuiujjki
// huikj uuoi uoijl iuuio uioio uoio rtyg
// hukui ijlio oi9o oiio ioio ioio ipop
// ygiuhuiu u8oiloi iloioio uiuil ijijijhukhu