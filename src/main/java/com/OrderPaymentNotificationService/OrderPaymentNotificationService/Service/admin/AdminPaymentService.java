package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.admin.AdminRefundRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.RedisLockService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy.PaymentGateway;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy.PaymentGatewayFactory;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin oversight over ALL payments/transactions. The refund action does NOT
 * reimplement gateway refund logic — it delegates to the same
 * {@link PaymentGateway#refundPayment} call used by
 * {@code PaymentController#refundPayment}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentRepository paymentRepo;
    private final PaymentGatewayFactory gatewayFactory;
    private final RedisLockService redisLockService;

    // ── GET /api/v1/admin/payments ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Object> getPayments(int page, int size, String status, String gateway, String dateFrom,
            String dateTo) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE));

        Payment.Status statusFilter = null;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                statusFilter = Payment.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return new ApiResponse<>(false, "Invalid status: " + status, null, 400);
            }
        }

        Transaction.Method methodFilter = null;
        if (gateway != null && !gateway.isBlank() && !gateway.equalsIgnoreCase("ALL")) {
            try {
                methodFilter = Transaction.Method.valueOf(gateway.toUpperCase());
            } catch (IllegalArgumentException e) {
                return new ApiResponse<>(false, "Invalid gateway/method: " + gateway, null, 400);
            }
        }

        Instant from;
        Instant to;
        try {
            from = dateFrom == null || dateFrom.isBlank() ? null
                    : LocalDate.parse(dateFrom).atStartOfDay(ZoneOffset.UTC).toInstant();
            to = dateTo == null || dateTo.isBlank() ? null
                    : LocalDate.parse(dateTo).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return new ApiResponse<>(false, "Invalid date format, expected yyyy-MM-dd", null, 400);
        }

        Specification<Payment> spec = buildPaymentSpec(statusFilter, methodFilter, from, to);
        Page<Payment> paymentPage = paymentRepo.findAll(spec, pageable);
        Page<Map<String, Object>> result = paymentPage.map(this::toSummary);

        Map<String, Object> payload = Map.of(
                "payments", result.getContent(),
                "currentPage", result.getNumber(),
                "pageSize", result.getSize(),
                "totalPayments", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "hasNext", result.hasNext());

        return new ApiResponse<>(true, "Payments fetched", payload, 200);
    }

    // ── GET /api/v1/admin/payments/{id} ───────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Object> getPaymentDetail(UUID paymentId) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
        return new ApiResponse<>(true, "Payment detail fetched", toDetail(payment), 200);
    }

    // ── POST /api/v1/admin/payments/{id}/refund ───────────────────────────────

    @Transactional
    public ApiResponse<Object> refund(UUID paymentId, AdminRefundRequest request) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));

        UUID transactionId = request.transactionId();
        if (transactionId == null) {
            if (payment.getTransactions() == null || payment.getTransactions().isEmpty()) {
                return new ApiResponse<>(false, "Payment has no transactions to refund", null, 400);
            }
            transactionId = payment.getTransactions().get(0).getId();
        }

        String amount = request.amount() != null && !request.amount().isBlank()
                ? request.amount()
                : payment.getPaidAmount();

        PaymentGateway paymentGateway;
        try {
            paymentGateway = gatewayFactory.getGateway(request.gateway());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 400);
        }

        UUID lockOwner = UUID.randomUUID();
        String lockKey = "lock:refund:" + transactionId;

        try {
            if (!redisLockService.acquireLock(lockKey, lockOwner, UUID.randomUUID(), 1, 2)) {
                return new ApiResponse<>(false, "Refund already in progress for this transaction.", null, 409);
            }

            log.info("Admin refund initiated | paymentId={} transactionId={} amount={} reason={}",
                    paymentId, transactionId, amount, request.reason());

            return paymentGateway.refundPayment(transactionId, amount);
        } finally {
            redisLockService.releaseLock("lock:refund:", lockOwner, UUID.randomUUID());
        }
    }

    // ── filtering ──────────────────────────────────────────────────────────────

    private Specification<Payment> buildPaymentSpec(Payment.Status status, Transaction.Method method,
            Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (method != null || from != null || to != null) {
                if (query != null) {
                    query.distinct(true);
                }
                Join<Payment, Transaction> tx = root.join("transactions", JoinType.LEFT);
                if (method != null) {
                    predicates.add(cb.equal(tx.get("method"), method));
                }
                if (from != null) {
                    predicates.add(cb.greaterThanOrEqualTo(tx.<java.time.ZonedDateTime>get("createdAt"),
                            from.atZone(ZoneOffset.UTC)));
                }
                if (to != null) {
                    predicates.add(cb.lessThan(tx.<java.time.ZonedDateTime>get("createdAt"),
                            to.atZone(ZoneOffset.UTC)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ── DTO helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> toSummary(Payment p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("paymentId", p.getId());
        m.put("bookingId", p.getBookingId());
        m.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        m.put("totalAmountPaise", p.getTotalAmount());
        m.put("paidAmountPaise", p.getPaidAmount());
        m.put("transactionCount", p.getTransactions() != null ? p.getTransactions().size() : 0);
        return m;
    }

    private Map<String, Object> toDetail(Payment p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("paymentId", p.getId());
        m.put("bookingId", p.getBookingId());
        m.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        m.put("totalAmountPaise", p.getTotalAmount());
        m.put("paidAmountPaise", p.getPaidAmount());
        List<Map<String, Object>> txs = p.getTransactions() == null ? List.of()
                : p.getTransactions().stream().map(this::toTxSummary).toList();
        m.put("transactions", txs);
        return m;
    }

    private Map<String, Object> toTxSummary(Transaction t) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("transactionId", t.getId());
        m.put("method", t.getMethod() != null ? t.getMethod().name() : null);
        m.put("status", t.getStatus() != null ? t.getStatus().name() : null);
        m.put("amountPaise", t.getAmount());
        m.put("orderId", t.getOrderId());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        return m;
    }
}
