package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy;

import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.CreateOrderDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment.Status;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction.Method;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.TransactionRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.PhonePeService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.PhonePeServiceImpl;

import lombok.RequiredArgsConstructor;

import java.util.*;

@Service("phonepeGateway")
@RequiredArgsConstructor

public class PhonePePaymentGateway implements PaymentGateway {
    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;

    private final PhonePeService phonePeService;

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
                System.out.println("📱 Creating PhonePe order...");
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
                System.err.println("Error is" + e.getMessage());
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

    public ApiResponse<Object> handleWebhook(Map<String, Object> payload) {
        System.out.println("📩 Handling PhonePe webhook: " + payload);
        // TODO: Verify checksum, update transaction in DB, etc.
        return new ApiResponse<>(true, "Done", Map.of(
                "transactionId", payload.get("transactionId"),
                "webhookStatus", "PROCESSED"), 201);
    }
}
// yguijjiuiuuijj 8oijnn