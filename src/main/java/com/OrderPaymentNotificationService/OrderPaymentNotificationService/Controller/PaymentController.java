package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CodConfirmRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CodOtpRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CodQrRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CreateOrderDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.CodPaymentService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.QrPaymentService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.RedisLockService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy.PaymentGateway;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy.PaymentGatewayFactory;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.filter.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentGatewayFactory gatewayFactory;
    private final RedisLockService redisLockService;
    private final CodPaymentService codPaymentService;
    private final QrPaymentService qrPaymentService;

    @PostMapping
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderDto dto) {
        log.info("Creating order for gateway: {}", dto.gateway());
        PaymentGateway paymentGateway = gatewayFactory.getGateway(dto.gateway());
        ApiResponse<Object> response = paymentGateway.createOrder(dto);
        return ResponseEntity.ok(response);
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

    // ══════════════════════════════════════════════════════════════════════════
    // COD – Generate OTP (ROLE_USER)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The customer generates a 6-digit OTP just before the delivery partner
     * arrives. The OTP is shown in the app and verbally given to the partner
     * who then calls /cod/confirm to finalise payment.
     *
     * Security: requires ROLE_USER JWT. The service verifies that the
     * transaction belongs to the authenticated user.
     */
    @PostMapping("/cod/generate-otp")
    public ResponseEntity<?> generateCodOtp(
            @Valid @RequestBody CodOtpRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String otp = codPaymentService.generateOtp(request.transactionId(), principal.getId());

        return ResponseEntity.ok(new ApiResponse<>(true, "OTP generated successfully",
                Map.of(
                        "otp", otp,
                        "expiresInMinutes", 10,
                        "transactionId", request.transactionId(),
                        "message", "Show this OTP to your delivery partner to confirm cash payment."),
                200));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COD – Confirm payment received (ROLE_DELIVERY)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Called by the delivery partner after collecting cash.
     * Validates the OTP provided by the customer and the collected amount,
     * then marks the COD transaction as SUCCESS.
     *
     * Security: requires ROLE_DELIVERY JWT. Idempotent – safe to retry if
     * the response is lost in transit.
     */
    @PostMapping("/cod/confirm")
    public ResponseEntity<?> confirmCodPayment(
            @Valid @RequestBody CodConfirmRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        codPaymentService.confirmPayment(request, principal.getId());

        return ResponseEntity.ok(new ApiResponse<>(true, "Cash payment confirmed successfully.",
                Map.of(
                        "transactionId", request.transactionId(),
                        "status", "SUCCESS",
                        "paymentMode", "CASH_ON_DELIVERY"),
                200));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COD – Generate payment QR (ROLE_DELIVERY)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Delivery partner calls this when the customer wants to pay digitally.
     * Returns a base64 QR image of a PhonePe checkout page.
     * Customer scans → pays with any UPI app → money goes to company account →
     * PhonePe webhook auto-confirms the transaction.
     *
     * Money flow: Customer ──UPI──► PhonePe ──► Company merchant account
     * Webhook fires ──► transaction marked SUCCESS ──► rider notified
     */
    @PostMapping("/cod/generate-payment-qr")
    public ResponseEntity<?> generateCodPaymentQr(
            @Valid @RequestBody CodQrRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Map<String, Object> result = qrPaymentService.generatePaymentQr(
                request.transactionId(), principal.getId());

        return ResponseEntity.ok(new ApiResponse<>(true, "Payment QR generated.", result, 200));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COD – Poll QR payment status (ROLE_DELIVERY)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Rider's app polls this after showing the QR to know when payment is received.
     * Recommended polling interval: every 3 seconds, timeout after 10 minutes.
     * When "paid": true, show "Payment Received" screen to the rider.
     */
    @GetMapping("/cod/qr-status/{transactionId}")
    public ResponseEntity<?> getCodQrStatus(
            @PathVariable UUID transactionId,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Map<String, Object> status = qrPaymentService.getQrPaymentStatus(
                transactionId, principal.getId());

        return ResponseEntity.ok(new ApiResponse<>(true, "Payment status fetched.", status, 200));
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
// jo899