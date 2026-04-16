package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CreateOrderDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.BaseService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ReceiptProducerService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.RedisLockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service("codGateway")
@RequiredArgsConstructor
@Slf4j
public class CodPaymentGateway extends BaseService implements PaymentGateway {

    private final PaymentRepository    paymentRepository;
    private final BookingRepository    bookingRepository;
    private final RedisLockService     redisLockService;
    private final ReceiptProducerService receiptProducerService;

    // ══════════════════════════════════════════════════════════════════════════
    // createOrder
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public ApiResponse<Object> createOrder(CreateOrderDto dto) {
        // guardDuplicatePayment(dto.bookingId());i will comment this

        Booking booking = loadAndValidateBooking(dto.bookingId());

        Payment payment = buildPayment(dto.bookingId(), booking.getTotalAmount());
        Transaction codTx = buildCodTransaction(payment, booking.getTotalAmount(), dto.bookingId());
        payment.setTransactions(List.of(codTx));
        paymentRepository.save(payment);

        booking.setStatus(Booking.Status.CONFIRMED);
        bookingRepository.save(booking);

        // releaseDuplicatePaymentLock(dto.bookingId()); i will comment this

        // Publish receipt generation event to Kafka
        receiptProducerService.publishForBooking(dto.bookingId());

        log.info("COD order created | bookingId={} paymentId={} amountPaise={}",
                dto.bookingId(), payment.getId(), booking.getTotalAmount());

        return new ApiResponse<>(true,
                "Order confirmed! Pay cash to the delivery partner on arrival.",
                Map.of(
                        "bookingId", dto.bookingId(),
                        "paymentId", payment.getId(),
                        "transactionId", codTx.getId(),
                        "paymentMode", "CASH_ON_DELIVERY",
                        "totalAmountPaise", booking.getTotalAmount(),
                        "status", "PENDING",
                        "instructions",
                        "Keep exact change ready. Generate an OTP before your delivery partner arrives."),
                201);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // refundPayment
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public ApiResponse<Object> refundPayment(UUID transactionId, String amount) {
        log.info("COD refund initiated | transactionId={} amount={}", transactionId, amount);
        return new ApiResponse<>(true, "COD refund initiated. Amount will be credited to your wallet.",
                Map.of(
                        "transactionId", transactionId,
                        "refundAmount", amount,
                        "refundMode", "WALLET_CREDIT",
                        "status", "REFUND_INITIATED"),
                202);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Not applicable for COD
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public ApiResponse<Object> validatePayment(UUID transactionId) {
        return new ApiResponse<>(false,
                "validatePayment is not applicable for Cash-on-Delivery orders.", null, 400);
    }

    @Override
    public ApiResponse<Object> handleWebhook(Map<String, Object> payload) {
        return new ApiResponse<>(false,
                "Webhooks are not applicable for Cash-on-Delivery.", null, 400);
    }

    @Override
    public ApiResponse<Object> handleWebhookForWallet(Map<String, Object> payload) {
        return new ApiResponse<>(false,
                "Wallet webhooks are not applicable for Cash-on-Delivery.", null, 400);
    }

    @Override
    public Map<String, Object> createOrder(String merchantId, String amountInPaises, String idempotencyKey) {
        throw new UnsupportedOperationException("COD orders do not use external merchant IDs.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    private Booking loadAndValidateBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (!booking.getUserId().equals(getUserId())) {
            throw new SecurityException("Booking does not belong to this user");
        }
        if (booking.getStatus() != Booking.Status.INITIATED) {
            throw new IllegalStateException(
                    "Booking is not in INITIATED state. Current state: " + booking.getStatus());
        }
        return booking;
    }

    private Payment buildPayment(UUID bookingId, String amountPaise) {
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setStatus(Payment.Status.PENDING);
        payment.setTotalAmount(amountPaise);
        return payment;
    }

    private Transaction buildCodTransaction(Payment payment, String amountPaise, UUID bookingId) {
        Transaction tx = new Transaction();
        tx.setPayment(payment);
        tx.setMethod(Transaction.Method.COD);
        tx.setAmount(amountPaise);
        tx.setTranscationNumber(UUID.randomUUID());
        tx.setStatus(Transaction.Status.PENDING);
        tx.setOrderId("COD-" + bookingId.toString().substring(0, 8).toUpperCase());
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
// u89uhuuhvu9uhuhhuu8huuhuiui huiiuj huiuj