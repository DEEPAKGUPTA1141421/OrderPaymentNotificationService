package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.OrderDto.OrderDetailDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.OrderDto.OrderSummaryDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.BookingItem;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin oversight over ALL bookings/orders — cross-shop, cross-user.
 * Reuses {@link BookingRepository}/{@link PaymentRepository} and the same
 * DTO shapes as {@code SellerBookingService} rather than duplicating
 * projection logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BookingRepository bookingRepo;
    private final PaymentRepository paymentRepo;

    // ── GET /api/v1/admin/orders ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Object> getOrders(int page, int size, String status, String dateFrom, String dateTo,
            String search) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Booking.Status statusFilter = null;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                statusFilter = Booking.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return new ApiResponse<>(false, "Invalid status: " + status, null, 400);
            }
        }

        Instant from;
        Instant to;
        try {
            from = parseStartOfDay(dateFrom);
            to = parseEndOfDay(dateTo);
        } catch (DateTimeParseException e) {
            return new ApiResponse<>(false, "Invalid date format, expected yyyy-MM-dd", null, 400);
        }

        Specification<Booking> spec = buildOrderSpec(statusFilter, from, to, search);
        Page<Booking> bookingPage = bookingRepo.findAll(spec, pageable);

        List<UUID> bookingIds = bookingPage.map(Booking::getId).toList();
        Map<UUID, Payment> paymentMap = batchLoadPayments(bookingIds);

        Page<OrderSummaryDto> result = bookingPage.map(b -> toSummaryDto(b, paymentMap.get(b.getId())));

        return new ApiResponse<>(true, "Orders fetched", buildListPayload(result), 200);
    }

    // ── GET /api/v1/admin/orders/{id} ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Object> getOrderDetail(UUID bookingId) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));

        Payment payment = paymentRepo.findFirstByBookingId(bookingId).orElse(null);

        return new ApiResponse<>(true, "Order detail fetched", toDetailDto(booking, payment), 200);
    }

    // ── PATCH /api/v1/admin/orders/{id}/status ────────────────────────────────

    /**
     * Admin-forced status change — intentionally bypasses
     * {@link Booking.Status#assertCanTransitionTo}, which governs the normal
     * customer/seller-driven state machine, since admins need to correct
     * orders stuck in an inconsistent state.
     */
    @Transactional
    public ApiResponse<Object> forceStatus(UUID bookingId, String newStatusStr, String reason) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));

        Booking.Status newStatus;
        try {
            newStatus = Booking.Status.valueOf(newStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "Invalid status: " + newStatusStr, null, 400);
        }

        Booking.Status previous = booking.getStatus();
        booking.setStatus(newStatus);
        bookingRepo.save(booking);

        log.info("Admin forced order status | bookingId={} {} -> {} reason={}",
                bookingId, previous, newStatus, reason);

        return new ApiResponse<>(true, "Status updated to " + newStatus.name(),
                Map.of("bookingId", bookingId, "previousStatus", previous.name(),
                        "status", newStatus.name(), "reason", reason == null ? "" : reason),
                200);
    }

    // ── POST /api/v1/admin/orders/{id}/cancel ─────────────────────────────────

    @Transactional
    public ApiResponse<Object> cancelOrder(UUID bookingId, String reason) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));

        if (booking.getStatus() == Booking.Status.CANCELLED) {
            return new ApiResponse<>(false, "Order is already cancelled", null, 400);
        }

        Booking.Status previous = booking.getStatus();
        booking.setStatus(Booking.Status.CANCELLED);
        bookingRepo.save(booking);

        log.info("Admin cancelled order | bookingId={} previousStatus={} reason={}",
                bookingId, previous, reason);

        return new ApiResponse<>(true, "Order cancelled",
                Map.of("bookingId", bookingId, "previousStatus", previous.name(),
                        "status", Booking.Status.CANCELLED.name(), "reason", reason),
                200);
    }

    // ── filtering ──────────────────────────────────────────────────────────────

    private Specification<Booking> buildOrderSpec(Booking.Status status, Instant from, Instant to, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), to));
            }
            if (search != null && !search.isBlank()) {
                String needle = search.trim();
                List<Predicate> searchPredicates = new ArrayList<>();
                try {
                    UUID asUuid = UUID.fromString(needle);
                    searchPredicates.add(cb.equal(root.get("id"), asUuid));
                    searchPredicates.add(cb.equal(root.get("userId"), asUuid));
                    searchPredicates.add(cb.equal(root.get("shopId"), asUuid));
                } catch (IllegalArgumentException ignored) {
                    // not a UUID — fall back to text search over address fields
                    searchPredicates.add(cb.like(cb.lower(root.<String>get("deliveryAddressText")),
                            "%" + needle.toLowerCase() + "%"));
                    searchPredicates.add(cb.like(cb.lower(root.<String>get("deliveryCity")),
                            "%" + needle.toLowerCase() + "%"));
                }
                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Instant parseStartOfDay(String date) {
        if (date == null || date.isBlank())
            return null;
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant parseEndOfDay(String date) {
        if (date == null || date.isBlank())
            return null;
        return LocalDate.parse(date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // ── DTO helpers (mirrors SellerBookingService's mapping) ────────────────────

    private Map<UUID, Payment> batchLoadPayments(List<UUID> ids) {
        if (ids.isEmpty())
            return Map.of();
        Map<UUID, Payment> result = new LinkedHashMap<>();
        for (Payment p : paymentRepo.findAllWithTransactionsByBookingIdIn(ids)) {
            result.putIfAbsent(p.getBookingId(), p);
        }
        return result;
    }

    private Map<String, Object> buildListPayload(Page<OrderSummaryDto> page) {
        return Map.of(
                "orders", page.getContent(),
                "currentPage", page.getNumber(),
                "pageSize", page.getSize(),
                "totalOrders", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "hasNext", page.hasNext());
    }

    private OrderSummaryDto toSummaryDto(Booking b, Payment payment) {
        BookingItem first = b.getItems().isEmpty() ? null : b.getItems().get(0);
        return new OrderSummaryDto(
                b.getId(),
                b.getShopId(),
                b.getStatus().name(),
                statusLabel(b.getStatus()),
                b.getItems().size(),
                b.getTotalAmount(),
                toRupeesStr(b.getTotalAmount()),
                payment != null ? payment.getStatus().name() : null,
                payment != null ? derivePaymentMode(payment) : "UNPAID",
                b.getExpiresAt(),
                b.getCreatedAt(),
                first != null ? first.getProductName() : null,
                first != null ? first.getProductImageUrl() : null);
    }

    private OrderDetailDto toDetailDto(Booking b, Payment payment) {
        List<OrderDetailDto.ItemDto> items = b.getItems().stream()
                .map(this::toItemDto)
                .toList();
        return new OrderDetailDto(
                b.getId(),
                b.getShopId(),
                b.getDeliveryAddress(),
                b.getStatus().name(),
                statusLabel(b.getStatus()),
                b.getTotalAmount(),
                toRupeesStr(b.getTotalAmount()),
                b.getExpiresAt(),
                b.getCreatedAt(),
                items,
                payment != null ? toPaymentDto(payment) : null);
    }

    private OrderDetailDto.ItemDto toItemDto(BookingItem item) {
        long lineTotal = Long.parseLong(item.getPrice()) * item.getQuantity();
        return new OrderDetailDto.ItemDto(
                item.getId(),
                item.getProductId(),
                item.getVariantId(),
                item.getProductName(),
                item.getProductImageUrl(),
                item.getQuantity(),
                item.getPrice(),
                toRupeesStr(item.getPrice()),
                String.valueOf(lineTotal),
                toRupeesStr(String.valueOf(lineTotal)));
    }

    private OrderDetailDto.PaymentDto toPaymentDto(Payment p) {
        List<OrderDetailDto.TransactionDto> txDtos = p.getTransactions().stream()
                .map(this::toTransactionDto)
                .toList();
        return new OrderDetailDto.PaymentDto(
                p.getId(),
                p.getStatus().name(),
                p.getTotalAmount(),
                toRupeesStr(p.getTotalAmount()),
                p.getPaidAmount(),
                txDtos);
    }

    private OrderDetailDto.TransactionDto toTransactionDto(Transaction tx) {
        return new OrderDetailDto.TransactionDto(
                tx.getId(),
                tx.getMethod().name(),
                tx.getStatus().name(),
                tx.getAmount(),
                toRupeesStr(tx.getAmount()),
                tx.getOrderId(),
                tx.getCreatedAt(),
                tx.getUpdatedAt());
    }

    private String derivePaymentMode(Payment p) {
        List<Transaction> txs = p.getTransactions();
        if (txs == null || txs.isEmpty())
            return "UNKNOWN";
        boolean hasCod = txs.stream().anyMatch(t -> t.getMethod() == Transaction.Method.COD);
        boolean hasGateway = txs.stream().anyMatch(t -> t.getMethod() == Transaction.Method.GATEWAY);
        boolean hasPoints = txs.stream().anyMatch(t -> t.getMethod() == Transaction.Method.POINTS);
        if (hasCod)
            return "COD";
        if (hasGateway && hasPoints)
            return "MIXED";
        if (hasGateway)
            return "ONLINE";
        if (hasPoints)
            return "POINTS";
        return "UNKNOWN";
    }

    private String statusLabel(Booking.Status s) {
        return switch (s) {
            case INITIATED -> "Initiated";
            case CONFIRMED -> "Confirmed";
            case PROCESSING -> "Processing";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
            case FAILED -> "Failed";
            case REVERSED -> "Return Initiated";
            case REVERSE_FAILED -> "Return Failed";
        };
    }

    private String toRupeesStr(String paise) {
        if (paise == null || paise.isBlank())
            return "0.00";
        try {
            return new java.math.BigDecimal(paise)
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (NumberFormatException e) {
            return "0.00";
        }
    }
}
