package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CreateOrderDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment.Status;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction.TransactionStatus;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Wallet;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.TransactionRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.WalletRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.WalletTransactionRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.BaseService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.PhonePeService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptProducerService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.RedisLockService;

import java.util.Optional;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant.WalletAndLoyalityPointsConstant.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service("phonepeGateway")
@RequiredArgsConstructor
@Slf4j
public class PhonePePaymentGateway extends BaseService implements PaymentGateway {

    private final PaymentRepository           paymentRepository;
    private final TransactionRepository       transactionRepository;
    private final PhonePeService              phonePeService;
    private final WalletTransactionRepository txRepo;
    private final WalletRepository            walletRepo;
    private final RedisLockService            redisLockService;
    private final ReceiptProducerService      receiptProducerService;

    @Value("${payment.webhook.secret}")
    private String webhookSecret;

    // ══════════════════════════════════════════════════════════════════════════
    //  createOrder
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public ApiResponse<Object> createOrder(CreateOrderDto dto) {
        guardDuplicatePayment(dto.bookingId());

        try {
            Payment payment = buildPayment(dto);
            List<Transaction> transactions = new ArrayList<>();

            Transaction pgTx    = createTransaction(payment, dto.pgPaymentAmount(), Transaction.Method.GATEWAY);
            Transaction pointsTx = null;
            if (dto.pointPayment()) {
                pointsTx = createTransaction(payment, dto.pointPaymentAmount(), Transaction.Method.POINTS);
            }

            if (dto.pgPayment()) {
                try {
                    log.info("Creating PhonePe order...");
                    Map<String, Object> phonePeResponse = phonePeService.createOrder(
                            pgTx.getTranscationNumber().toString(),
                            dto.pgPaymentAmount(),
                            dto.idempotencyKey());

                    payment.setStatus(Status.PENDING);
                    Object orderIdObj = phonePeResponse.get("orderId");
                    if (orderIdObj != null) {
                        pgTx.setOrderId(orderIdObj.toString());
                    }
                } catch (Exception e) {
                    log.error("PhonePe order creation failed: {}", e.getMessage(), e);
                    return new ApiResponse<>(false, "Failed to create PhonePe order", null, 500);
                }
            }

            transactions.add(pgTx);
            if (pointsTx != null) transactions.add(pointsTx);
            payment.setTransactions(transactions);
            paymentRepository.save(payment);

            log.info("PhonePe order created | bookingId={} paymentId={}", dto.bookingId(), payment.getId());
            return new ApiResponse<>(true, "Payment Created Successfully",
                    Map.of("bookingId", dto.bookingId(), "paymentId", payment.getId()), 201);

        } finally {
            releaseDuplicatePaymentLock(dto.bookingId());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  validatePayment / refundPayment
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public ApiResponse<Object> validatePayment(UUID merchantOrderId) {
        Map<String, Object> phonePeResponse = phonePeService.checkOrderStatus(merchantOrderId.toString());
        return new ApiResponse<>(true, "Validate Payment", phonePeResponse, 200);
    }

    @Override
    public ApiResponse<Object> refundPayment(UUID transactionId, String amount) {
        return new ApiResponse<>(true, "Refund Is In The Queue",
                Map.of("transactionId", transactionId, "refundedAmount", amount, "status", "REFUND_INITIATED"), 201);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Webhooks
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    @Override
    public ApiResponse<Object> handleWebhookForWallet(Map<String, Object> payload) {
        String merchantOrderId = String.valueOf(payload.get(MERCHANT_ORDER_ID));
        String state           = String.valueOf(payload.get("state"));
        log.info("Wallet top-up webhook: merchantOrderId={}, state={}", merchantOrderId, state);

        UUID txnId = UUID.fromString(merchantOrderId);
        WalletTransaction txn = txRepo.findById(txnId)
                .orElseThrow(() -> new IllegalArgumentException("No wallet transaction found: " + merchantOrderId));

        if (txn.getStatus() != TransactionStatus.PENDING) {
            log.warn("Webhook received for non-PENDING txn {} (status={}). Ignoring.", txnId, txn.getStatus());
            return new ApiResponse<>(true, "Transaction already processed", null, 200);
        }

        if (COMPLETED.equalsIgnoreCase(state)) {
            Wallet wallet = walletRepo.findByUserIdForUpdate(txn.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Wallet not found for user: " + txn.getUserId()));

            wallet.credit(txn.getAmountPaise());
            walletRepo.save(wallet);

            txn.setStatus(TransactionStatus.SUCCESS);
            txn.setClosingBalancePaise(wallet.getBalancePaise());
            txRepo.save(txn);

            log.info("Wallet credited successfully for txnId={}", txnId);
            return new ApiResponse<>(true, "Wallet credited successfully", null, 200);
        }

        if (FAILED.equalsIgnoreCase(state)) {
            txn.setStatus(TransactionStatus.FAILED);
            txRepo.save(txn);
            log.warn("Payment failed for txnId={}", txnId);
            return new ApiResponse<>(true, "Transaction marked as failed", null, 200);
        }

        return new ApiResponse<>(true, "Webhook received, state: " + state, null, 200);
    }

    /**
     * Handles PhonePe payment completion webhooks for:
     *   1. COD orders paid via QR at doorstep
     *   2. Regular online payment orders
     *
     * PhonePe sends the merchantOrderId we originally passed when creating the order.
     * We look up the transaction by transcationNumber (UUID), verify idempotency,
     * then mark it SUCCESS or FAILED.
     *
     * Money flow: Customer → PhonePe → Company's merchant account → webhook fires here.
     */
    @Transactional
    @Override
    public ApiResponse<Object> handleWebhook(Map<String, Object> payload) {
        log.info("PhonePe webhook received: {}", payload);

        String merchantOrderId = String.valueOf(payload.get(MERCHANT_ORDER_ID));
        String state           = String.valueOf(payload.get("state"));

        if (merchantOrderId == null || merchantOrderId.isBlank() || "null".equals(merchantOrderId)) {
            log.warn("Webhook missing merchantOrderId — payload: {}", payload);
            return new ApiResponse<>(false, "Missing merchantOrderId", null, 400);
        }

        UUID txnUUID;
        try {
            txnUUID = UUID.fromString(merchantOrderId);
        } catch (IllegalArgumentException e) {
            log.warn("merchantOrderId is not a UUID: {}", merchantOrderId);
            return new ApiResponse<>(false, "Invalid merchantOrderId format", null, 400);
        }

        Optional<Transaction> txOpt = transactionRepository.findByTranscationNumber(txnUUID);
        if (txOpt.isEmpty()) {
            log.warn("No transaction found for merchantOrderId={}", merchantOrderId);
            return new ApiResponse<>(false, "Transaction not found", null, 404);
        }

        Transaction tx = txOpt.get();

        if (tx.getStatus() == Transaction.Status.SUCCESS || tx.getStatus() == Transaction.Status.FAILED) {
            log.info("Webhook already processed (idempotent) | txId={} status={}", tx.getId(), tx.getStatus());
            return new ApiResponse<>(true, "Already processed", null, 200);
        }

        Payment payment = tx.getPayment();

        if (COMPLETED.equalsIgnoreCase(state)) {
            tx.setStatus(Transaction.Status.SUCCESS);
            transactionRepository.save(tx);

            payment.setStatus(Payment.Status.SUCCESS);
            payment.setPaidAmount(tx.getAmount());
            paymentRepository.save(payment);

            // Publish receipt generation event to Kafka
            receiptProducerService.publishForBooking(payment.getBookingId());

            log.info("Payment confirmed via PhonePe webhook | txId={} paymentId={} method={}",
                    tx.getId(), payment.getId(), tx.getMethod());
            return new ApiResponse<>(true, "Payment confirmed", null, 200);
        }

        if (FAILED.equalsIgnoreCase(state)) {
            tx.setStatus(Transaction.Status.FAILED);
            transactionRepository.save(tx);

            payment.setStatus(Payment.Status.FAILED);
            paymentRepository.save(payment);

            log.warn("Payment failed via PhonePe webhook | txId={} paymentId={}", tx.getId(), payment.getId());
            return new ApiResponse<>(true, "Payment marked failed", null, 200);
        }

        log.info("Webhook received with unhandled state '{}' | txId={}", state, tx.getId());
        return new ApiResponse<>(true, "Webhook received, state: " + state, null, 200);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  External PhonePe order creation (used by QrPaymentService)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public Map<String, Object> createOrder(String merchantId, String amountInPaises, String idempotencyKey) {
        try {
            log.info("Creating PhonePe order for merchantId={}", merchantId);
            return phonePeService.createOrder(merchantId, amountInPaises, idempotencyKey);
        } catch (Exception e) {
            log.error("PhonePe createOrder error: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    private Payment buildPayment(CreateOrderDto dto) {
        double total = 0.0;
        if (dto.pgPaymentAmount() != null && !dto.pgPaymentAmount().isEmpty()) {
            total += Double.parseDouble(dto.pgPaymentAmount());
        }
        if (dto.pointPaymentAmount() != null && !dto.pointPaymentAmount().isEmpty()) {
            total += Double.parseDouble(dto.pointPaymentAmount());
        }

        Payment payment = new Payment();
        payment.setBookingId(dto.bookingId());
        payment.setStatus(Payment.Status.INITIATED);
        payment.setTotalAmount(String.valueOf(total));
        return payment;
    }

    private Transaction createTransaction(Payment payment, String amount, Transaction.Method method) {
        Transaction tx = new Transaction();
        tx.setPayment(payment);
        tx.setMethod(method);
        tx.setAmount(amount);
        if (method == Transaction.Method.GATEWAY) {
            tx.setTranscationNumber(UUID.randomUUID());
        }
        tx.setStatus(Transaction.Status.INITIATED);
        return tx;
    }

    private void guardDuplicatePayment(UUID bookingId) {
        boolean locked = redisLockService.acquireLock(
                "lock:payment:", getUserId(), bookingId, 1, 2);
        if (!locked) {
            throw new IllegalStateException("Payment already in progress for this booking. Please wait.");
        }
    }

    private void releaseDuplicatePaymentLock(UUID bookingId) {
        redisLockService.releaseLock("lock:payment:", getUserId(), bookingId);
    }

    private boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return constantTimeEquals(computed, signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
