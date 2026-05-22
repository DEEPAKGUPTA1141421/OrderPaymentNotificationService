package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.OrderDto.OrderDetailDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.OrderDto.OrderSummaryDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.BookingItem;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingItemRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerBookingService extends BaseService {

    private static final int MAX_PAGE_SIZE = 50;

    private final BookingRepository     bookingRepo;
    private final PaymentRepository     paymentRepo;
    private final BookingItemRepository bookingItemRepo;

    // ── GET /api/v1/seller/orders ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> getShopOrders(int page, int size, String status) {
        UUID shopId   = getUserId();
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "expiresAt")
        );

        Page<Booking> bookingPage;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                Booking.Status s = Booking.Status.valueOf(status.toUpperCase());
                bookingPage = bookingRepo.findByShopIdAndStatus(shopId, s, pageable);
            } catch (IllegalArgumentException e) {
                return new ApiResponse<>(false, "Invalid status: " + status, null, 400);
            }
        } else {
            bookingPage = bookingRepo.findByShopId(shopId, pageable);
        }

        List<UUID> bookingIds = bookingPage.map(Booking::getId).toList();
        Map<UUID, Payment> paymentMap = batchLoadPayments(bookingIds);

        Page<OrderSummaryDto> result = bookingPage.map(
                b -> toSummaryDto(b, paymentMap.get(b.getId())));

        return new ApiResponse<>(true, "Orders fetched", buildListPayload(result), 200);
    }

    // ── PUT /api/v1/booking/{bookingId}/status ────────────────────────────────

    @Transactional
    public ApiResponse<Object> updateOrderStatus(UUID bookingId, String newStatusStr) {
        UUID shopId = getUserId();

        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));

        if (!booking.getShopId().equals(shopId)) {
            return new ApiResponse<>(false, "Access denied", null, 403);
        }

        Booking.Status newStatus;
        try {
            newStatus = Booking.Status.valueOf(newStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "Invalid status: " + newStatusStr, null, 400);
        }

        try {
            booking.getStatus().assertCanTransitionTo(newStatus);
        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 422);
        }

        booking.setStatus(newStatus);
        bookingRepo.save(booking);

        log.info("Order status updated | bookingId={} shopId={} newStatus={}", bookingId, shopId, newStatus);
        return new ApiResponse<>(true, "Status updated to " + newStatus.name(), Map.of("status", newStatus.name()), 200);
    }

    // ── GET /api/v1/seller/stats?days=7 ──────────────────────────────────────

    @Transactional
    public ApiResponse<Object> getShopStats(int days) {
        UUID shopId  = getUserId();
        int  clampedDays = Math.min(Math.max(1, days), 365);

        // Status breakdown (all-time)
        List<Object[]> statusCounts = bookingRepo.countByStatusForShop(shopId);
        Map<String, Long> statusMap = new HashMap<>();
        for (Object[] row : statusCounts) {
            statusMap.put(((Booking.Status) row[0]).name(), (Long) row[1]);
        }

        long totalOrders   = statusMap.values().stream().mapToLong(Long::longValue).sum();
        long pendingOrders = statusMap.getOrDefault("CONFIRMED", 0L);
        long revenuePaise  = bookingRepo.sumRevenueByShopId(shopId);

        // Current period window
        Instant now          = Instant.now();
        Instant currentStart = now.minus(clampedDays - 1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant prevStart    = currentStart.minus(clampedDays, ChronoUnit.DAYS);

        // Daily chart for current period
        List<Object[]> daily = bookingRepo.dailyStatsForShopSince(shopId, currentStart);
        List<Map<String, Object>> chart = daily.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("day",          row[0].toString());
            entry.put("orders",       ((Number) row[1]).longValue());
            entry.put("revenuePaise", ((Number) row[2]).longValue());
            entry.put("revenueRupees", new BigDecimal(((Number) row[2]).longValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString());
            return entry;
        }).toList();

        // Period totals for delta computation
        Object[] curr = bookingRepo.sumOrdersAndRevenueForPeriod(shopId, currentStart, now);
        Object[] prev = bookingRepo.sumOrdersAndRevenueForPeriod(shopId, prevStart, currentStart);

        long currOrders  = curr != null && curr[0] != null ? ((Number) curr[0]).longValue() : 0L;
        long currRev     = curr != null && curr[1] != null ? ((Number) curr[1]).longValue() : 0L;
        long prevOrders  = prev != null && prev[0] != null ? ((Number) prev[0]).longValue() : 0L;
        long prevRev     = prev != null && prev[1] != null ? ((Number) prev[1]).longValue() : 0L;

        double ordersChange  = prevOrders == 0 ? 0 : ((double)(currOrders  - prevOrders)  / prevOrders)  * 100;
        
        double revenueChange = prevRev    == 0 ? 0 : ((double)(currRev     - prevRev)     / prevRev)     * 100;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalOrders",    totalOrders);
        stats.put("pendingOrders",  pendingOrders);
        stats.put("totalRevenuePaise",  revenuePaise);
        stats.put("totalRevenueRupees", new BigDecimal(revenuePaise)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString());
        stats.put("statusBreakdown",  statusMap);
        stats.put("dailyChart",       chart);
        stats.put("days",             clampedDays);
        stats.put("ordersChange",     Math.round(ordersChange  * 10.0) / 10.0);
        stats.put("revenueChange",    Math.round(revenueChange * 10.0) / 10.0);

        return new ApiResponse<>(true, "Stats fetched", stats, 200);
    }

    // ── GET /api/v1/seller/orders/status-counts ───────────────────────────────
    public ApiResponse<Object> getStatusCounts() {
        UUID shopId = getUserId();
        List<Object[]> rows = bookingRepo.countByStatusForShop(shopId);
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : rows) {
            String name = ((Booking.Status) row[0]).name();
            long   cnt  = (Long) row[1];
            counts.put(name, cnt);
            total += cnt;
        }
        counts.put("ALL", total);
        return new ApiResponse<>(true, "Status counts", counts, 200);
    }

    // ── GET /api/v1/seller/earnings ───────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> getSellerEarnings() {
        UUID shopId = getUserId();

        long earnedPaise  = bookingRepo.sumDeliveredByShopId(shopId);
        long pendingPaise = bookingRepo.sumPendingByShopId(shopId);

        // Last 10 delivered orders as settlement history
        Page<Booking> recentPage = bookingRepo.findByShopIdAndStatus(
                shopId, Booking.Status.DELIVERED,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<UUID> ids = recentPage.map(Booking::getId).toList();
        Map<UUID, Payment> payMap = batchLoadPayments(ids);

        List<Map<String, Object>> settlements = recentPage.getContent().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bookingId",  b.getId().toString());
            m.put("shortId",    b.getId().toString().substring(0, 8).toUpperCase());
            m.put("totalPaise", b.getTotalAmount());
            m.put("totalRupees", toRupeesStr(b.getTotalAmount()));
            m.put("itemCount",  b.getItems().size());
            m.put("settledAt",  b.getCreatedAt());
            Payment p = payMap.get(b.getId());
            m.put("paymentMode", p != null ? derivePaymentMode(p) : "UNKNOWN");
            return m;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalEarnedPaise",  earnedPaise);
        data.put("totalEarnedRupees", toRupeesStr(String.valueOf(earnedPaise)));
        data.put("pendingPaise",      pendingPaise);
        data.put("pendingRupees",     toRupeesStr(String.valueOf(pendingPaise)));
        data.put("recentSettlements", settlements);

        return new ApiResponse<>(true, "Earnings fetched", data, 200);
    }

    // ── GET /api/v1/seller/orders/{bookingId} ─────────────────────────────────

    @Transactional
    public ApiResponse<Object> getSellerOrderDetail(UUID bookingId) {
        UUID shopId = getUserId();

        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));

        if (!booking.getShopId().equals(shopId)) {
            return new ApiResponse<>(false, "Access denied", null, 403);
        }

        Payment payment = paymentRepo.findFirstByBookingId(bookingId).orElse(null);

        return new ApiResponse<>(true, "Order detail fetched", toDetailDto(booking, payment), 200);
    }

    // ── GET /api/v1/seller/stats/top-products?limit=5 ────────────────────────

    @Transactional
    public ApiResponse<Object> getTopProducts(int limit) {
        UUID shopId      = getUserId();
        int clampedLimit = Math.min(Math.max(1, limit), 20);

        List<Object[]> rows = bookingItemRepo.topProductsByRevenue(shopId, clampedLimit);

        List<Map<String, Object>> products = rows.stream().map(row -> {
            long revPaise = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productId",      row[0] != null ? row[0].toString() : null);
            entry.put("productName",    row[1] != null ? row[1].toString() : "Unknown");
            entry.put("totalQty",       row[2] != null ? ((Number) row[2]).longValue() : 0L);
            entry.put("revenuePaise",   revPaise);
            entry.put("revenueRupees",  new BigDecimal(revPaise)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString());
            return entry;
        }).toList();

        return new ApiResponse<>(true, "Top products fetched", products, 200);
    }

    // ── DTO helpers ───────────────────────────────────────────────────────────

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

    private Map<UUID, Payment> batchLoadPayments(List<UUID> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return paymentRepo.findAllWithTransactionsByBookingIdIn(ids)
                .stream()
                .collect(Collectors.toMap(Payment::getBookingId, Function.identity(), (a, b) -> a));
    }

    private Map<String, Object> buildListPayload(Page<OrderSummaryDto> page) {
        return Map.of(
                "orders",      page.getContent(),
                "currentPage", page.getNumber(),
                "pageSize",    page.getSize(),
                "totalOrders", page.getTotalElements(),
                "totalPages",  page.getTotalPages(),
                "hasNext",     page.hasNext());
    }

    private String toRupeesStr(String paise) {
        if (paise == null || paise.isBlank()) return "0.00";
        try {
            return new BigDecimal(paise)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (NumberFormatException e) {
            return "0.00";
        }
    }

    private String statusLabel(Booking.Status s) {
        return switch (s) {
            case INITIATED        -> "Initiated";
            case CONFIRMED        -> "Confirmed";
            case PROCESSING       -> "Processing";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED        -> "Delivered";
            case CANCELLED        -> "Cancelled";
            case FAILED           -> "Failed";
            case REVERSED         -> "Return Initiated";
            case REVERSE_FAILED   -> "Return Failed";
        };
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
        if (txs == null || txs.isEmpty()) return "UNKNOWN";
        boolean hasCod     = txs.stream().anyMatch(t -> t.getMethod() == Transaction.Method.COD);
        boolean hasGateway = txs.stream().anyMatch(t -> t.getMethod() == Transaction.Method.GATEWAY);
        boolean hasPoints  = txs.stream().anyMatch(t -> t.getMethod() == Transaction.Method.POINTS);
        if (hasCod) return "COD";
        if (hasGateway && hasPoints) return "MIXED";
        if (hasGateway) return "ONLINE";
        if (hasPoints) return "POINTS";
        return "UNKNOWN";
    }
}
