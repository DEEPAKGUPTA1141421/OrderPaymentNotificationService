package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.OrderDto.OrderDetailDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.OrderDto.OrderSummaryDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.BookingItem;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingQueryService extends BaseService {

    private static final int MAX_PAGE_SIZE = 50;

    private final BookingRepository bookingRepo;
    private final PaymentRepository paymentRepo;

    // ══════════════════════════════════════════════════════════════════════════
    // Order list GET /api/v1/booking
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns a paginated list of the authenticated user's bookings, most recent
     * first. Payment info (status + mode) is batch-fetched in a single query to
     * avoid N+1.
     */
    @Transactional
    public ApiResponse<Object> getMyOrders(int page, int size) {
        UUID userId = getUserId();
        Pageable pageable = buildPageable(page, size);

        Page<Booking> bookingPage = bookingRepo.findByUserId(userId, pageable);

        // Batch-load payments with transactions — one query for the whole page
        List<UUID> bookingIds = bookingPage.map(Booking::getId).toList();
        Map<UUID, Payment> paymentByBookingId = batchLoadPayments(bookingIds);

        Page<OrderSummaryDto> result = bookingPage.map(
                b -> toSummaryDto(b, paymentByBookingId.get(b.getId())));

        log.info("Order list fetched | userId={} page={} size={} total={}",
                userId, page, size, bookingPage.getTotalElements());

        return new ApiResponse<>(true, "Orders fetched successfully", buildListPayload(result), 200);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Order detail GET /api/v1/booking/{bookingId}
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns full booking details for the authenticated user.
     * Ownership is verified — a user cannot view another user's order.
     */
    @Transactional
    public ApiResponse<Object> getOrderDetail(UUID bookingId) {
        UUID userId = getUserId();

        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + bookingId));

        if (!booking.getUserId().equals(userId)) {
            log.warn("Ownership check failed | userId={} bookingId={}", userId, bookingId);
            throw new SecurityException("You do not have access to this order");
        }

        Payment payment = paymentRepo.findFirstByBookingId(bookingId).orElse(null);

        log.info("Order detail fetched | userId={} bookingId={}", userId, bookingId);
        return new ApiResponse<>(true, "Order details fetched successfully", toDetailDto(booking, payment), 200);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DTO mapping helpers
    // ══════════════════════════════════════════════════════════════════════════

    private OrderSummaryDto toSummaryDto(Booking b, Payment payment) {
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
                b.getCreatedAt());
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

    // ══════════════════════════════════════════════════════════════════════════
    // Utility helpers
    // ══════════════════════════════════════════════════════════════════════════

    private Map<UUID, Payment> batchLoadPayments(List<UUID> bookingIds) {
        if (bookingIds.isEmpty())
            return Collections.emptyMap();
        return paymentRepo.findAllWithTransactionsByBookingIdIn(bookingIds)
                .stream()
                .collect(Collectors.toMap(
                        Payment::getBookingId,
                        Function.identity(),
                        (existing, duplicate) -> existing // keep first if somehow duplicated
                ));
    }

    private Pageable buildPageable(int page, int size) {
        int clampedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        // Sort by expiresAt DESC — always non-null, same relative order as createdAt
        // (expiresAt = createdAt + 5 min). Avoids NULLS LAST which Criteria API
        // rejects.
        Sort sort = Sort.by(Sort.Direction.DESC, "expiresAt");
        return PageRequest.of(Math.max(0, page), clampedSize, sort);
    }

    /** Derives the display payment mode from the transaction methods present. */
    private String derivePaymentMode(Payment payment) {
        List<Transaction> txs = payment.getTransactions();
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

    /** Human-readable status label for the UI. */
    private String statusLabel(Booking.Status status) {
        return switch (status) {
            case INITIATED -> "Initiated";
            case CONFIRMED -> "Confirmed";
            case CANCELLED -> "Cancelled";
            case FAILED -> "Failed";
            case REVERSED -> "Return Initiated";
            case REVERSE_FAILED -> "Return Failed";
            case PROCESSING -> "Processing";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
        };
    }

    /** Converts a paise string to a rupee decimal string. Safe against nulls. */
    private String toRupeesStr(String paise) {
        if (paise == null || paise.isBlank())
            return "0.00";
        try {
            return new BigDecimal(paise)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (NumberFormatException e) {
            return "0.00";
        }
    }

    /**
     * Wraps Page metadata so the frontend gets pagination info without Spring
     * internals.
     */
    private Map<String, Object> buildListPayload(Page<OrderSummaryDto> page) {
        return Map.of(
                "orders", page.getContent(),
                "currentPage", page.getNumber(),
                "pageSize", page.getSize(),
                "totalOrders", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "hasNext", page.hasNext(),
                "hasPrevious", page.hasPrevious());
    }
}
