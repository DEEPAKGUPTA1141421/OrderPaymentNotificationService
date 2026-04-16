package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CodConfirmRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * Business logic for Cash-on-Delivery flows:
 *  1. generateOtp  – called by the customer just before delivery; stores a
 *                    short-lived 6-digit OTP in Redis that the delivery partner
 *                    must present to confirm payment collection.
 *  2. confirmPayment – called by the delivery partner (ROLE_DELIVERY);
 *                    validates OTP + amount, then marks the transaction/payment
 *                    as SUCCESS atomically.
 *
 * Security controls:
 *  - OTP is 6 digits, cryptographically random (SecureRandom).
 *  - OTP TTL: 10 minutes in Redis.
 *  - Max 3 OTP regenerations per transaction (rate-limit key in Redis).
 *  - Distributed lock (2-second TTL) prevents concurrent confirmation races.
 *  - OTP comparison uses constant-time equality to resist timing attacks.
 *  - Collected amount must exactly match the stored transaction amount.
 *  - Idempotent: re-confirming an already-SUCCESS transaction is a no-op.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodPaymentService {

    // ── Redis key prefixes ─────────────────────────────────────────────────────
    private static final String OTP_PREFIX          = "cod:otp:";          // cod:otp:{transactionId}
    private static final String OTP_ATTEMPT_PREFIX  = "cod:otp:attempt:";  // cod:otp:attempt:{transactionId}
    private static final String CONFIRM_LOCK_PREFIX = "cod:lock:confirm:"; // cod:lock:confirm:{transactionId}

    // ── Tunables ───────────────────────────────────────────────────────────────
    private static final long OTP_TTL_MINUTES    = 10;
    private static final int  MAX_OTP_ATTEMPTS   = 3;  // regenerations per transaction
    private static final long CONFIRM_LOCK_SECS  = 2;  // distributed lock TTL

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final TransactionRepository transactionRepository;
    private final PaymentRepository     paymentRepository;
    private final BookingRepository     bookingRepository;
    private final StringRedisTemplate   redisTemplate;
    private final RedisLockService      redisLockService;

    // ══════════════════════════════════════════════════════════════════════════
    //  1.  Generate OTP   (ROLE_USER endpoint)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Generates and stores a 6-digit COD OTP for the given transaction.
     *
     * @param transactionId the COD transaction belonging to the user
     * @param userId        extracted from the JWT of the calling user
     * @return the plain-text OTP to show in the app (displayed once)
     */
    @Transactional
    public String generateOtp(UUID transactionId, UUID userId) {

        // 1. Load transaction
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        // 2. Guard: must be a COD transaction
        if (tx.getMethod() != Transaction.Method.COD) {
            throw new IllegalStateException("OTP can only be generated for COD transactions");
        }

        // 3. Guard: must still be PENDING (not yet paid / not failed)
        if (tx.getStatus() != Transaction.Status.PENDING) {
            throw new IllegalStateException(
                    "Cannot generate OTP for transaction in state: " + tx.getStatus());
        }

        // 4. Verify booking ownership (lazy-loads payment within @Transactional)
        Payment payment = tx.getPayment();
        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Booking not found: " + payment.getBookingId()));

        if (!booking.getUserId().equals(userId)) {
            throw new SecurityException("Transaction does not belong to this user");
        }

        // 5. Rate-limit OTP regenerations
        String attemptKey = OTP_ATTEMPT_PREFIX + transactionId;
        String rawAttempts = redisTemplate.opsForValue().get(attemptKey);
        if (rawAttempts != null && Integer.parseInt(rawAttempts) >= MAX_OTP_ATTEMPTS) {
            throw new IllegalStateException(
                    "OTP generation limit reached for this order. Contact support.");
        }

        // 6. Generate cryptographically random 6-digit OTP
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        // 7. Persist OTP in Redis with TTL (overwrites any previous OTP)
        redisTemplate.opsForValue().set(
                OTP_PREFIX + transactionId, otp, Duration.ofMinutes(OTP_TTL_MINUTES));

        // 8. Increment attempt counter (keep it alive at least as long as OTP)
        redisTemplate.opsForValue().increment(attemptKey);
        redisTemplate.expire(attemptKey, Duration.ofMinutes(OTP_TTL_MINUTES));

        log.info("COD OTP generated | transactionId={} userId={}", transactionId, userId);
        return otp;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  2.  Confirm Payment   (ROLE_DELIVERY endpoint)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Called by the delivery partner after collecting cash from the customer.
     * Validates the OTP and amount, then finalises the transaction.
     *
     * @param req          confirmation payload (transactionId, otp, amount)
     * @param deliveryBoyId extracted from the JWT of the delivery partner
     */
    @Transactional
    public void confirmPayment(CodConfirmRequest req, UUID deliveryBoyId) {

        // 1. Acquire distributed lock to prevent race conditions
        boolean locked = redisLockService.acquireLock(
                CONFIRM_LOCK_PREFIX, deliveryBoyId, req.transactionId(), 1, CONFIRM_LOCK_SECS);
        if (!locked) {
            throw new IllegalStateException(
                    "Payment confirmation already in progress for this transaction. Please retry.");
        }

        try {
            // 2. Load transaction
            Transaction tx = transactionRepository.findById(req.transactionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Transaction not found: " + req.transactionId()));

            // 3. Guard: must be a COD transaction
            if (tx.getMethod() != Transaction.Method.COD) {
                throw new IllegalStateException("Not a COD transaction");
            }

            // 4. Idempotency check – already confirmed, return silently
            if (tx.getStatus() == Transaction.Status.SUCCESS) {
                log.info("COD already confirmed (idempotent) | transactionId={}", req.transactionId());
                return;
            }

            // 5. Guard: must be PENDING
            if (tx.getStatus() != Transaction.Status.PENDING) {
                throw new IllegalStateException(
                        "Cannot confirm transaction in state: " + tx.getStatus());
            }

            // 6. Validate OTP (constant-time comparison to resist timing attacks)
            String storedOtp = redisTemplate.opsForValue().get(OTP_PREFIX + req.transactionId());
            if (storedOtp == null) {
                throw new IllegalStateException(
                        "OTP has expired or was never generated. Ask customer to regenerate.");
            }
            if (!constantTimeEquals(storedOtp, req.otp())) {
                log.warn("COD OTP mismatch | transactionId={} deliveryBoyId={}",
                        req.transactionId(), deliveryBoyId);
                throw new IllegalStateException("Invalid OTP");
            }

            // 7. Validate collected amount matches expected amount (exact match in paise)
            if (!tx.getAmount().equals(req.collectedAmountPaise())) {
                throw new IllegalStateException(
                        "Collected amount mismatch. Expected " + tx.getAmount()
                                + " paise, got " + req.collectedAmountPaise() + " paise.");
            }

            // 8. Mark transaction as SUCCESS
            tx.setStatus(Transaction.Status.SUCCESS);
            transactionRepository.save(tx);

            // 9. Mark payment as SUCCESS and record paid amount
            Payment payment = tx.getPayment();
            payment.setStatus(Payment.Status.SUCCESS);
            payment.setPaidAmount(tx.getAmount());
            paymentRepository.save(payment);

            // 10. Invalidate OTP (single-use)
            redisTemplate.delete(OTP_PREFIX + req.transactionId());

            log.info("COD payment confirmed | transactionId={} paymentId={} deliveryBoyId={}",
                    req.transactionId(), payment.getId(), deliveryBoyId);

        } finally {
            redisLockService.releaseLock(CONFIRM_LOCK_PREFIX, deliveryBoyId, req.transactionId());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Constant-time string comparison to prevent timing-based OTP oracle attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
