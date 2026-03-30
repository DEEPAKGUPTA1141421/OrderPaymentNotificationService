package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CreateOrderDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment.Status;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction.Method;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction.TransactionStatus;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Wallet;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.TransactionRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.WalletRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.WalletTransactionRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.PhonePeService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.PhonePeServiceImpl;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.WalletService;

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
public class PhonePePaymentGateway implements PaymentGateway {
    private final PaymentRepository paymentRepository;
    private final PhonePeService phonePeService;
    private final WalletTransactionRepository txRepo;
    private final WalletRepository walletRepo;
    @Value("${payment.webhook.secret}")
    private String webhookSecret;

    @Override
    public ApiResponse<Object> createOrder(CreateOrderDto dto) {
        Payment payment = new Payment();
        payment.setBookingId(dto.bookingId());
        payment.setStatus(Payment.Status.INITIATED);

        double total = 0.0;
        if (dto.pgPaymentAmount() != null && !dto.pgPaymentAmount().isEmpty()) {
            total += Double.parseDouble(dto.pgPaymentAmount());
        }
        if (dto.pointPaymentAmount() != null && !dto.pointPaymentAmount().isEmpty()) {
            total += Double.parseDouble(dto.pointPaymentAmount());
        }
        payment.setTotalAmount(String.valueOf(total));
        List<Transaction> transactions = new ArrayList<>();
        Transaction pgTx = createTransaction(payment, dto.pgPaymentAmount(), Transaction.Method.GATEWAY);
        Transaction pointsTx = null;
        if (dto.pointPayment())

        {
            pointsTx = createTransaction(payment, dto.pointPaymentAmount(), Transaction.Method.POINTS);
        }

        // ✅ Call PhonePe API after payment and transaction saved
        if (dto.pgPayment()) {
            try {
                log.info("📱 Creating PhonePe order...");
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
                e.printStackTrace();
                log.error("Error is" + e.getMessage());
                return new ApiResponse<>(false, "Failed to create PhonePe order", null, 500);
            }
        }
        transactions.add(pgTx);
        if (pointsTx != null)
            transactions.add(pointsTx);
        payment.setTransactions(transactions);
        paymentRepository.save(payment);
        return new ApiResponse<>(true, "Payment Created Successfully",
                Map.of("bookingId", dto.bookingId(), "paymentId", payment.getId(), "transactions", "transactions"),
                201);
    }

    private Transaction createTransaction(Payment payment, String amount, Transaction.Method paymentMethod) {
        Transaction tx = new Transaction();
        tx.setPayment(payment);
        tx.setMethod(paymentMethod);
        tx.setAmount(amount);
        if (paymentMethod == Transaction.Method.GATEWAY)
            tx.setTranscationNumber(UUID.randomUUID());
        tx.setStatus(Transaction.Status.INITIATED);
        return tx;
    }

    @Override
    public ApiResponse<Object> validatePayment(UUID merchantOrderId) {
        System.out.println("🔍 Validating PhonePe payment...");
        Map<String, Object> phonePeResponse = phonePeService.checkOrderStatus(merchantOrderId.toString());
        return new ApiResponse<>(true, "Validate Payment", phonePeResponse, 201);
    }

    @Override
    public ApiResponse<Object> refundPayment(UUID transactionId, String amount) {
        System.out.println("💸 Processing refund via PhonePe...");
        // TODO: Replace with actual PhonePe refund API
        return new ApiResponse<Object>(true, "Refund Is In The Queeu", Map.of(
                "transactionId", transactionId,
                "refundedAmount", amount,
                "status", "REFUND_INITIATED"), 201);
    }

    @Transactional
    public ApiResponse<Object> handleWebhookForWallet(Map<String, Object> payload) {
        String merchantOrderId = String.valueOf(payload.get(MERCHANT_ORDER_ID));
        String state = String.valueOf(payload.get("state"));
        log.info("Wallet top-up webhook: merchantOrderId={}, state={}", merchantOrderId, state);

        UUID txnId = UUID.fromString(merchantOrderId);
        WalletTransaction txn = txRepo.findById(txnId)
                .orElseThrow(() -> new IllegalArgumentException("No wallet transaction found: " + merchantOrderId));

        // 1. Idempotency Check
        if (txn.getStatus() != TransactionStatus.PENDING) {
            log.warn("Webhook received for non-PENDING txn {} (status={}). Ignoring.", txnId, txn.getStatus());
            return new ApiResponse<>(true, "Transaction already processed", null, 200);
        }

        // 2. Handle SUCCESS
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

        // 3. Handle FAILURE
        else if (FAILED.equalsIgnoreCase(state)) {
            txn.setStatus(TransactionStatus.FAILED);
            txRepo.save(txn);
            log.warn("Payment failed for txnId={}", txnId);
            return new ApiResponse<>(true, "Transaction marked as failed", null, 200);
        }

        // 4. Handle UNKNOWN or PENDING states
        return new ApiResponse<>(true, "Webhook received, state: " + state, null, 200);
    }

    public ApiResponse<Object> handleWebhook(Map<String, Object> payload) {
        log.info("PhonePe webhook received: {}", payload);
        // TODO: verify X-VERIFY checksum header from PhonePe before trusting payload
        return new ApiResponse<>(true, "Webhook processed", null, 200);
    }

    private boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isBlank())
            return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(computed, signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length())
            return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public Map<String, Object> createOrder(String merchantId, String amountInPaises, String Idempotentkey) {
        Map<String, Object> phonePeResponse = new HashMap<>();
        try {
            log.info("Creating PhonePe order...");
            phonePeResponse = phonePeService.createOrder(
                    merchantId,
                    amountInPaises,
                    Idempotentkey);
            return phonePeResponse;
        } catch (Exception e) {
            e.printStackTrace();
            log.info("Error is" + e.getMessage());
            return phonePeResponse;
        }
    }
}
// yguijjiuiuuijj 8oijnn