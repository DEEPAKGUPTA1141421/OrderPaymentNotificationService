package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.math.BigDecimal;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.TransactionRepository;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepo;
    private final TransactionRepository txRepo;

    @Transactional
    public ApiResponse<Payment> makeCompositePayment(UUID bookingId, String totalAmount, String pointsAmount,
            String pgAmount, UUID userId) {
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setTotalAmount(totalAmount);
        payment.setStatus(Payment.Status.PENDING);
        paymentRepo.save(payment);

        // --- Transaction 1: Points ---
        Transaction pointsTx = new Transaction();
        pointsTx.setPayment(payment);
        pointsTx.setMethod(Transaction.Method.POINTS);
        pointsTx.setAmount(pointsAmount);
        // Simulate wallet deduction
        boolean walletSuccess = deductPoints(userId, new BigDecimal(pointsAmount));
        pointsTx.setStatus(walletSuccess ? Transaction.Status.SUCCESS : Transaction.Status.FAILED);
        txRepo.save(pointsTx);
        // --- Transaction 2: Payment Gateway ---
        Transaction pgTx = new Transaction();
        pgTx.setPayment(payment);
        pgTx.setMethod(Transaction.Method.GATEWAY);
        pgTx.setAmount(pgAmount);
        boolean pgSuccess = processGatewayPayment(new BigDecimal(pgAmount));
        pgTx.setStatus(pgSuccess ? Transaction.Status.SUCCESS : Transaction.Status.FAILED);
        txRepo.save(pgTx);

        // --- Final Payment Status ---
        if (walletSuccess && pgSuccess) {
            payment.setStatus(Payment.Status.SUCCESS);
            payment.setPaidAmount(totalAmount);
        } else {
            payment.setStatus(Payment.Status.FAILED);
            // if failed, rollback points if already deducted
            if (walletSuccess)
                refundPoints(userId, new BigDecimal(pointsAmount));
        }
        paymentRepo.save(payment);

        return new ApiResponse<>(
                payment.getStatus() == Payment.Status.SUCCESS,
                "Composite payment " + payment.getStatus(),
                payment,
                200);
    }

    // Dummy implementations
    private boolean deductPoints(UUID userId, BigDecimal amount) {
        System.out.println("Deducting " + amount + " points for user " + userId);
        return true; // assume success
    }

    private void refundPoints(UUID userId, BigDecimal amount) {
        System.out.println("Refunding " + amount + " points to user " + userId);
    }

    private boolean processGatewayPayment(BigDecimal amount) {
        System.out.println("Calling PG for amount: " + amount);
        return true; // assume PG success
    }
}
