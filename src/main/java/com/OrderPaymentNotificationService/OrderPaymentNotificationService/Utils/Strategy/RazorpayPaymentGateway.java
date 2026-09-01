package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy;

import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CreateOrderDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.TransactionRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.BaseService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.RazorpayService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptProducerService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.RedisLockService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.SellerNotificationEvents;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Strategy for the Razorpay gateway — mirrors {@link PhonePePaymentGateway}.
 *
 * Flow: createOrder() opens a Razorpay order (receipt = our transaction's
 * transcationNumber) → client launches Razorpay Checkout with the returned
 * orderId/keyId → on success the app calls validatePayment(), which asks
 * Razorpay's Orders API for the authoritative status (server-to-server, so
 * it isn't trusting client-supplied values) → Razorpay's webhook is a second,
 * asynchronous path to the same confirmation for cases where the app is
 * killed before it can call validatePayment().
 */
@Service("razorpayGateway")
@RequiredArgsConstructor
@Slf4j
public class RazorpayPaymentGateway extends BaseService implements PaymentGateway {

    private final PaymentRepository      paymentRepository;
    private final TransactionRepository  transactionRepository;
    private final BookingRepository      bookingRepository;
    private final RazorpayService        razorpayService;
    private final RedisLockService       redisLockService;
    private final ReceiptProducerService receiptProducerService;
    private final SellerNotificationEvents sellerNotificationEvents;

    // ══════════════════════════════════════════════════════════════════════════
    //  createOrder
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public ApiResponse<Object> createOrder(CreateOrderDto dto) {
        guardDuplicatePayment(dto.bookingId());

        try {
            Payment payment = buildPayment(dto);
            List<Transaction> transactions = new ArrayList<>();

            Transaction pgTx = createTransaction(payment, dto.pgPaymentAmount(), Transaction.Method.GATEWAY);
            Transaction pointsTx = null;
            if (dto.pointPayment()) {
                pointsTx = createTransaction(payment, dto.pointPaymentAmount(), Transaction.Method.POINTS);
            }

            Map<String, Object> razorpayOrder = null;
            if (dto.pgPayment()) {
                try {
                    log.info("Creating Razorpay order...");
                    razorpayOrder = razorpayService.createOrder(
                            pgTx.getTranscationNumber().toString(),
                            dto.pgPaymentAmount(),
                            dto.idempotencyKey());

                    payment.setStatus(Payment.Status.PENDING);
                    Object orderIdObj = razorpayOrder.get("orderId");
                    if (orderIdObj != null) {
                        pgTx.setOrderId(orderIdObj.toString());
                    }
                } catch (Exception e) {
                    log.error("Razorpay order creation failed: {}", e.getMessage(), e);
                    return new ApiResponse<>(false, "Failed to create Razorpay order", null, 500);
                }
            }

            transactions.add(pgTx);
            if (pointsTx != null) transactions.add(pointsTx);
            payment.setTransactions(transactions);
            paymentRepository.save(payment);

            log.info("Razorpay order created | bookingId={} paymentId={}", dto.bookingId(), payment.getId());

            Map<String, Object> data = new HashMap<>();
            data.put("bookingId", dto.bookingId());
            data.put("paymentId", payment.getId());
            data.put("transactionId", pgTx.getTranscationNumber());
            if (razorpayOrder != null) {
                data.put("razorpayOrderId", razorpayOrder.get("orderId"));
                data.put("razorpayKeyId", razorpayOrder.get("keyId"));
                data.put("razorpayAmount", razorpayOrder.get("amount"));
                data.put("razorpayCurrency", razorpayOrder.get("currency"));
            }

            return new ApiResponse<>(true, "Payment Created Successfully", data, 201);

        } finally {
            releaseDuplicatePaymentLock(dto.bookingId());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  validatePayment / refundPayment
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public ApiResponse<Object> validatePayment(UUID merchantOrderId) {
        Optional<Transaction> txOpt = transactionRepository.findByTranscationNumber(merchantOrderId);
        if (txOpt.isEmpty()) {
            return new ApiResponse<>(false, "Transaction not found", Map.of("state", "FAILED"), 404);
        }

        Transaction tx = txOpt.get();

        if (tx.getStatus() == Transaction.Status.SUCCESS) {
            return new ApiResponse<>(true, "Already verified",
                    Map.of("state", "COMPLETED", "orderId", tx.getOrderId()), 200);
        }
        if (tx.getStatus() == Transaction.Status.FAILED) {
            return new ApiResponse<>(true, "Payment failed",
                    Map.of("state", "FAILED", "orderId", tx.getOrderId()), 200);
        }

        Map<String, Object> orderStatus = razorpayService.checkOrderStatus(tx.getOrderId());
        String status = String.valueOf(orderStatus.get("status"));

        if ("paid".equalsIgnoreCase(status)) {
            markSuccess(tx);
            return new ApiResponse<>(true, "Payment verified",
                    Map.of("state", "COMPLETED", "orderId", tx.getOrderId()), 200);
        }

        return new ApiResponse<>(true, "Payment pending",
                Map.of("state", "PENDING", "orderId", tx.getOrderId()), 200);
    }

    @Override
    public ApiResponse<Object> refundPayment(UUID transactionId, String amount) {
        return new ApiResponse<>(true, "Refund Is In The Queue",
                Map.of("transactionId", transactionId, "refundedAmount", amount, "status", "REFUND_INITIATED"), 201);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Webhooks
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Razorpay webhook payload shape:
     * { "event": "payment.captured", "payload": { "payment": { "entity": {
     *     "id", "order_id", "status", "notes": { "merchantOrderId": "<uuid>" } } } } }
     * We tag every order with notes.merchantOrderId = our transaction's
     * transcationNumber at creation time, so the webhook can find it back.
     */
    @Transactional
    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<Object> handleWebhook(Map<String, Object> payload) {
        log.info("Razorpay webhook received: {}", payload);

        String event = String.valueOf(payload.get("event"));

        Map<String, Object> payloadInner = (Map<String, Object>) payload.get("payload");
        if (payloadInner == null) {
            return new ApiResponse<>(false, "Missing payload", null, 400);
        }
        Map<String, Object> paymentWrap = (Map<String, Object>) payloadInner.get("payment");
        if (paymentWrap == null) {
            return new ApiResponse<>(true, "Webhook received, event: " + event, null, 200);
        }
        Map<String, Object> entity = (Map<String, Object>) paymentWrap.get("entity");
        if (entity == null) {
            return new ApiResponse<>(false, "Missing payment entity", null, 400);
        }

        Map<String, Object> notes = (Map<String, Object>) entity.get("notes");
        String merchantOrderId = notes != null ? String.valueOf(notes.get("merchantOrderId")) : null;

        if (merchantOrderId == null || merchantOrderId.isBlank() || "null".equals(merchantOrderId)) {
            log.warn("Razorpay webhook missing notes.merchantOrderId — payload: {}", payload);
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

        if ("payment.captured".equals(event)) {
            markSuccess(tx);
            log.info("Payment confirmed via Razorpay webhook | txId={} paymentId={}",
                    tx.getId(), tx.getPayment().getId());
            return new ApiResponse<>(true, "Payment confirmed", null, 200);
        }

        if ("payment.failed".equals(event)) {
            markFailed(tx);
            log.warn("Payment failed via Razorpay webhook | txId={} paymentId={}",
                    tx.getId(), tx.getPayment().getId());
            return new ApiResponse<>(true, "Payment marked failed", null, 200);
        }

        log.info("Webhook received with unhandled event '{}' | txId={}", event, tx.getId());
        return new ApiResponse<>(true, "Webhook received, event: " + event, null, 200);
    }

    @Override
    public ApiResponse<Object> handleWebhookForWallet(Map<String, Object> payload) {
        return new ApiResponse<>(false, "Wallet top-up via Razorpay is not supported.", null, 400);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  External Razorpay order creation (kept for interface parity with PhonePe)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public Map<String, Object> createOrder(String merchantId, String amountInPaises, String idempotencyKey) {
        try {
            log.info("Creating Razorpay order for merchantId={}", merchantId);
            return razorpayService.createOrder(merchantId, amountInPaises, idempotencyKey);
        } catch (Exception e) {
            log.error("Razorpay createOrder error: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void markSuccess(Transaction tx) {
        tx.setStatus(Transaction.Status.SUCCESS);
        transactionRepository.save(tx);

        Payment payment = tx.getPayment();
        payment.setStatus(Payment.Status.SUCCESS);
        payment.setPaidAmount(tx.getAmount());
        paymentRepository.save(payment);

        receiptProducerService.publishForBooking(payment.getBookingId());

        bookingRepository.findById(payment.getBookingId()).ifPresent(booking -> {
            sellerNotificationEvents.notifyNewOrder(booking);
            sellerNotificationEvents.notifyPaymentReceived(booking);
        });
    }

    private void markFailed(Transaction tx) {
        tx.setStatus(Transaction.Status.FAILED);
        transactionRepository.save(tx);

        Payment payment = tx.getPayment();
        payment.setStatus(Payment.Status.FAILED);
        paymentRepository.save(payment);

        bookingRepository.findById(payment.getBookingId())
                .ifPresent(sellerNotificationEvents::notifyPaymentFailed);
    }

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
}
