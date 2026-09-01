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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerBookingService extends BaseService {

    private static final int MAX_PAGE_SIZE = 50;
    /** Number of points in the "Current balance" sparklines (Earning/Customer/Payouts cards). */
    private static final int SPARKLINE_DAYS = 7;

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
        // 3650 (~10y) doubles as the effective "all time" ceiling for the seller app's period picker.
        int  clampedDays = Math.min(Math.max(1, days), 3650);

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
        List<Object[]> currList = bookingRepo.sumOrdersAndRevenueForPeriod(shopId, currentStart, now);
        List<Object[]> prevList = bookingRepo.sumOrdersAndRevenueForPeriod(shopId, prevStart, currentStart);
        Object[] curr = currList.isEmpty() ? null : currList.get(0);
        Object[] prev = prevList.isEmpty() ? null : prevList.get(0);

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

        // Earning sparkline — always the last 7 calendar days of revenue (rupees),
        // independent of the `days` window, for the "Current balance" Earning card.
        Instant sparkSince = now.minus(SPARKLINE_DAYS - 1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        List<Object[]> earningRows = bookingRepo.dailyStatsForShopSince(shopId, sparkSince);
        List<Double> earningSparkline = buildSparkline(
                earningRows.stream().map(row -> new Object[]{row[0], row[2]}).toList(),
                sparkSince, SPARKLINE_DAYS, true);
        stats.put("earningSparkline", earningSparkline);

        return new ApiResponse<>(true, "Stats fetched", stats, 200);
    }

    // ── GET /api/v1/seller/stats/customers?days=7 ─────────────────────────────

    @Transactional
    public ApiResponse<Object> getCustomerStats(int days) {
        UUID shopId      = getUserId();
        // 3650 (~10y) doubles as the effective "all time" ceiling for the seller app's period picker.
        int  clampedDays = Math.min(Math.max(1, days), 3650);

        Instant now          = Instant.now();
        Instant currentStart = now.minus(clampedDays - 1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant prevStart    = currentStart.minus(clampedDays, ChronoUnit.DAYS);

        long totalCustomers = bookingRepo.countDistinctCustomersByShopId(shopId);

        long currCustomers = bookingRepo.countDistinctCustomersForPeriod(shopId, currentStart, now);
        long prevCustomers = bookingRepo.countDistinctCustomersForPeriod(shopId, prevStart, currentStart);
        long newCustomers  = bookingRepo.countNewCustomersForPeriod(shopId, currentStart, now);
        long returningCustomers = Math.max(0, currCustomers - newCustomers);

        double customersChange = prevCustomers == 0 ? 0
                : ((double) (currCustomers - prevCustomers) / prevCustomers) * 100;

        // Monthly trend over the last 12 months, for the chart
        Instant trendSince = now.minus(365, ChronoUnit.DAYS);
        List<Object[]> monthlyRows = bookingRepo.monthlyDistinctCustomers(shopId, trendSince);
        List<Map<String, Object>> monthlyTrend = monthlyRows.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month",     row[0].toString());
            entry.put("customers", ((Number) row[1]).longValue());
            return entry;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalCustomers",      totalCustomers);
        data.put("newCustomers",        newCustomers);
        data.put("returningCustomers",  returningCustomers);
        data.put("customersChangePercent", Math.round(customersChange * 10.0) / 10.0);
        data.put("monthlyTrend",        monthlyTrend);
        data.put("days",                clampedDays);

        // Customer sparkline — daily new-customer counts for the last 7 days, for the
        // "Current balance" Customer card.
        Instant sparkSince = now.minus(SPARKLINE_DAYS - 1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        List<Object[]> newCustRows = bookingRepo.dailyNewCustomers(shopId, sparkSince);
        List<Double> sparkline = buildSparkline(newCustRows, sparkSince, SPARKLINE_DAYS, false);
        data.put("sparkline", sparkline);

        return new ApiResponse<>(true, "Customer stats fetched", data, 200);
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
        long totalSalesPaise = bookingRepo.sumRevenueByShopId(shopId);

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
        data.put("totalSalesPaise",   totalSalesPaise);
        data.put("totalSalesRupees",  toRupeesStr(String.valueOf(totalSalesPaise)));
        data.put("recentSettlements", settlements);

        // Week-over-week deltas for the three Earnings-page stat cards (Earning / Balance / Total value of sales).
        Instant now          = Instant.now();
        Instant currentStart = now.minus(6, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant prevStart    = currentStart.minus(7, ChronoUnit.DAYS);

        long currEarned = bookingRepo.sumDeliveredForPeriod(shopId, currentStart, now);
        long prevEarned = bookingRepo.sumDeliveredForPeriod(shopId, prevStart, currentStart);
        long currPending = bookingRepo.sumPendingForPeriod(shopId, currentStart, now);
        long prevPending = bookingRepo.sumPendingForPeriod(shopId, prevStart, currentStart);
        List<Object[]> currSalesList = bookingRepo.sumOrdersAndRevenueForPeriod(shopId, currentStart, now);
        List<Object[]> prevSalesList = bookingRepo.sumOrdersAndRevenueForPeriod(shopId, prevStart, currentStart);
        long currSales = currSalesList.isEmpty() || currSalesList.get(0)[1] == null ? 0L : ((Number) currSalesList.get(0)[1]).longValue();
        long prevSales = prevSalesList.isEmpty() || prevSalesList.get(0)[1] == null ? 0L : ((Number) prevSalesList.get(0)[1]).longValue();

        data.put("earnedChangePercent",     pctDelta(prevEarned, currEarned));
        data.put("balanceChangePercent",    pctDelta(prevPending, currPending));
        data.put("totalSalesChangePercent", pctDelta(prevSales, currSales));

        // Sparklines for the three stat cards — last 7 calendar days.
        Instant sparkSince = now.minus(SPARKLINE_DAYS - 1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        List<Object[]> earnedRows  = bookingRepo.dailyDeliveredRevenue(shopId, sparkSince);
        List<Object[]> pendingRows = bookingRepo.dailyPendingRevenue(shopId, sparkSince);
        List<Object[]> salesRows   = bookingRepo.dailyStatsForShopSince(shopId, sparkSince);
        data.put("sparkline", buildSparkline(earnedRows, sparkSince, SPARKLINE_DAYS, true));
        data.put("balanceSparkline", buildSparkline(pendingRows, sparkSince, SPARKLINE_DAYS, true));
        data.put("totalSalesSparkline", buildSparkline(
                salesRows.stream().map(row -> new Object[]{row[0], row[2]}).toList(),
                sparkSince, SPARKLINE_DAYS, true));

        return new ApiResponse<>(true, "Earnings fetched", data, 200);
    }

    private double pctDelta(long prev, long curr) {
        double pct = prev == 0 ? 0 : ((double) (curr - prev) / prev) * 100;
        return Math.round(pct * 10.0) / 10.0;
    }

    // ── GET /api/v1/seller/earnings/history?page=&size= ───────────────────────

    @Transactional
    public ApiResponse<Object> getSellerEarningsHistory(int page, int size) {
        UUID shopId = getUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));

        Page<Object[]> rows = bookingRepo.dailyEarningsBreakdown(shopId, pageable);
        Map<String, String> methodByKey = buildMethodLookup(shopId);

        List<Map<String, Object>> history = rows.getContent().stream().map(row -> {
            String day     = row[0].toString();
            String bucket  = row[1].toString();
            long earningsPaise = ((Number) row[4]).longValue();
            long withdrawnPaise = "PAID".equals(bucket) ? earningsPaise : 0L;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date",                 day);
            m.put("status",               bucket);
            m.put("orderCount",           ((Number) row[2]).longValue());
            m.put("productSalesCount",    ((Number) row[3]).longValue());
            m.put("earningsPaise",        earningsPaise);
            m.put("earningsRupees",       toRupeesStr(String.valueOf(earningsPaise)));
            m.put("method",               methodByKey.getOrDefault(day + "|" + bucket, "UNKNOWN"));
            m.put("amountWithdrawnPaise", withdrawnPaise);
            m.put("amountWithdrawnRupees", toRupeesStr(String.valueOf(withdrawnPaise)));
            return m;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("history",     history);
        data.put("currentPage", rows.getNumber());
        data.put("hasMore",     rows.hasNext());

        return new ApiResponse<>(true, "Earnings history fetched", data, 200);
    }

    /** Builds a "yyyy-MM-dd|PAID|PENDING" -> dominant payment-method label lookup. */
    private Map<String, String> buildMethodLookup(UUID shopId) {
        List<Object[]> rows = bookingRepo.dailyPaymentModeBreakdown(shopId);
        Map<String, String> result = new HashMap<>();
        for (Object[] row : rows) {
            String key = row[0].toString() + "|" + row[1].toString();
            long cod     = ((Number) row[2]).longValue();
            long gateway = ((Number) row[3]).longValue();
            long points  = ((Number) row[4]).longValue();

            String label;
            if (cod == 0 && gateway == 0 && points == 0) {
                label = "UNKNOWN";
            } else if (gateway > 0 && points > 0 && cod == 0) {
                label = "MIXED";
            } else if (cod >= gateway && cod >= points) {
                label = "COD";
            } else if (gateway >= points) {
                label = "ONLINE";
            } else {
                label = "POINTS";
            }
            result.put(key, label);
        }
        return result;
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
            long revPaise = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productId",       row[0] != null ? row[0].toString() : null);
            entry.put("productName",     row[1] != null ? row[1].toString() : "Unknown");
            entry.put("productImageUrl", row[2] != null ? row[2].toString() : null);
            entry.put("totalQty",        row[3] != null ? ((Number) row[3]).longValue() : 0L);
            entry.put("revenuePaise",    revPaise);
            entry.put("revenueRupees",   new BigDecimal(revPaise)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString());
            return entry;
        }).toList();

        return new ApiResponse<>(true, "Top products fetched", products, 200);
    }

    /**
     * Builds a fixed-length, oldest-to-newest sparkline from {day, value} rows, filling
     * in zeros for any day in the window that has no row. {@code toRupees} converts a
     * paise value (long) to rupees (double); pass {@code false} for plain counts.
     */
    private List<Double> buildSparkline(List<Object[]> rows, Instant since, int points, boolean toRupees) {
        Map<LocalDate, Double> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = LocalDate.parse(row[0].toString());
            double value = ((Number) row[1]).doubleValue();
            byDay.put(day, value);
        }
        LocalDate start = since.atZone(ZoneOffset.UTC).toLocalDate();
        List<Double> result = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            LocalDate day = start.plusDays(i);
            double value = byDay.getOrDefault(day, 0.0);
            result.add(toRupees ? Math.round(value) / 100.0 : value);
        }
        return result;
    }

    // ── DTO helpers ───────────────────────────────────────────────────────────

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
