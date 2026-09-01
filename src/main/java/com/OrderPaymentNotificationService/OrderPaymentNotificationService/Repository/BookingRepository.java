package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Paginated list of all bookings for a given user. Sorted via Pageable. */
    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    /** All orders placed at a given shop (seller view). */
    Page<Booking> findByShopId(UUID shopId, Pageable pageable);

    /** Orders at a shop filtered by status. */
    Page<Booking> findByShopIdAndStatus(UUID shopId, Booking.Status status, Pageable pageable);

    /** Total revenue for a shop (sum of totalAmount in paise for confirmed+ orders). */
    @Query("""
            SELECT COALESCE(SUM(CAST(b.totalAmount AS long)), 0)
            FROM Booking b
            WHERE b.shopId = :shopId
              AND b.status IN ('CONFIRMED','PROCESSING','OUT_FOR_DELIVERY','DELIVERED')
            """)
    long sumRevenueByShopId(@Param("shopId") UUID shopId);

    /** Order count per day for the last N days (for chart). */
    @Query(value = """
            SELECT DATE(b.created_at) AS day,
                   COUNT(*)           AS orderCount,
                   COALESCE(SUM(CAST(b.total_amount AS BIGINT)), 0) AS revenuePaise
            FROM bookings b
            WHERE b.shop_id = :shopId
              AND b.status IN ('CONFIRMED','PROCESSING','OUT_FOR_DELIVERY','DELIVERED')
              AND b.created_at >= :since
            GROUP BY DATE(b.created_at)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> dailyStatsForShop(@Param("shopId") UUID shopId, @Param("since") Instant since);

    /** Count by status for a shop — used by the dashboard. */
    @Query("SELECT b.status, COUNT(b) FROM Booking b WHERE b.shopId = :shopId GROUP BY b.status")
    List<Object[]> countByStatusForShop(@Param("shopId") UUID shopId);

    /** Order count and revenue for a shop within a time window — used for period deltas. */
    @Query("""
            SELECT COUNT(b), COALESCE(SUM(CAST(b.totalAmount AS long)), 0)
            FROM Booking b
            WHERE b.shopId = :shopId
              AND b.status IN ('CONFIRMED','PROCESSING','OUT_FOR_DELIVERY','DELIVERED')
              AND b.createdAt >= :from
              AND b.createdAt < :to
            """)
    List<Object[]> sumOrdersAndRevenueForPeriod(
            @Param("shopId") UUID shopId,
            @Param("from")   Instant from,
            @Param("to")     Instant to);

    /** Sum of totalAmount for DELIVERED orders — settled seller earnings. */
    @Query("""
            SELECT COALESCE(SUM(CAST(b.totalAmount AS long)), 0)
            FROM Booking b
            WHERE b.shopId = :shopId
              AND b.status = 'DELIVERED'
            """)
    long sumDeliveredByShopId(@Param("shopId") UUID shopId);

    /** Sum of totalAmount for in-flight orders (not yet settled). */
    @Query("""
            SELECT COALESCE(SUM(CAST(b.totalAmount AS long)), 0)
            FROM Booking b
            WHERE b.shopId = :shopId
              AND b.status IN ('CONFIRMED', 'PROCESSING', 'OUT_FOR_DELIVERY')
            """)
    long sumPendingByShopId(@Param("shopId") UUID shopId);

    /** Daily chart over an arbitrary window (parameterised days). */
    @Query(value = """
            SELECT DATE(b.created_at) AS day,
                   COUNT(*)           AS orderCount,
                   COALESCE(SUM(CAST(b.total_amount AS BIGINT)), 0) AS revenuePaise
            FROM bookings b
            WHERE b.shop_id = :shopId
              AND b.status IN ('CONFIRMED','PROCESSING','OUT_FOR_DELIVERY','DELIVERED')
              AND b.created_at >= :since
            GROUP BY DATE(b.created_at)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> dailyStatsForShopSince(
            @Param("shopId") UUID shopId,
            @Param("since")  Instant since);

    /** All-time distinct customer count for a shop. */
    @Query("SELECT COUNT(DISTINCT b.userId) FROM Booking b WHERE b.shopId = :shopId")
    long countDistinctCustomersByShopId(@Param("shopId") UUID shopId);

    /** Distinct customers who placed at least one order for this shop within [from, to). */
    @Query("SELECT COUNT(DISTINCT b.userId) FROM Booking b " +
           "WHERE b.shopId = :shopId AND b.createdAt >= :from AND b.createdAt < :to")
    long countDistinctCustomersForPeriod(
            @Param("shopId") UUID shopId,
            @Param("from")   Instant from,
            @Param("to")     Instant to);

    /**
     * Distinct customers within [from, to) whose *first-ever* order for this shop
     * also falls in that window (i.e. brand-new customers, not repeat buyers).
     */
    @Query("""
            SELECT COUNT(DISTINCT b.userId) FROM Booking b
            WHERE b.shopId = :shopId AND b.createdAt >= :from AND b.createdAt < :to
              AND NOT EXISTS (
                  SELECT 1 FROM Booking b2
                  WHERE b2.shopId = b.shopId AND b2.userId = b.userId AND b2.createdAt < :from
              )
            """)
    long countNewCustomersForPeriod(
            @Param("shopId") UUID shopId,
            @Param("from")   Instant from,
            @Param("to")     Instant to);

    /** Distinct customer count per calendar month since a given instant — for the trend chart. */
    @Query(value = """
            SELECT DATE_TRUNC('month', b.created_at) AS month,
                   COUNT(DISTINCT b.user_id)          AS customerCount
            FROM bookings b
            WHERE b.shop_id = :shopId
              AND b.created_at >= :since
            GROUP BY DATE_TRUNC('month', b.created_at)
            ORDER BY month ASC
            """, nativeQuery = true)
    List<Object[]> monthlyDistinctCustomers(
            @Param("shopId") UUID shopId,
            @Param("since")  Instant since);

    /**
     * New-customer count per day since a given instant — a customer is "new" on the
     * calendar day of their first-ever order for this shop. Used for the 7-point
     * "Customer" sparkline on the Current-balance section.
     */
    @Query(value = """
            SELECT first_day, COUNT(*) AS newCustomers
            FROM (
                SELECT b.user_id AS user_id, MIN(DATE(b.created_at)) AS first_day
                FROM bookings b
                WHERE b.shop_id = :shopId
                GROUP BY b.user_id
            ) t
            WHERE first_day >= DATE(:since)
            GROUP BY first_day
            ORDER BY first_day ASC
            """, nativeQuery = true)
    List<Object[]> dailyNewCustomers(
            @Param("shopId") UUID shopId,
            @Param("since")  Instant since);

    /**
     * Revenue per day (paise) for DELIVERED (settled) orders since a given instant —
     * used as the net-earnings proxy for the "Payouts" sparkline, since there is no
     * separate payout event stream.
     */
    @Query(value = """
            SELECT DATE(b.created_at) AS day,
                   COALESCE(SUM(CAST(b.total_amount AS BIGINT)), 0) AS revenuePaise
            FROM bookings b
            WHERE b.shop_id = :shopId
              AND b.status = 'DELIVERED'
              AND b.created_at >= :since
            GROUP BY DATE(b.created_at)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> dailyDeliveredRevenue(
            @Param("shopId") UUID shopId,
            @Param("since")  Instant since);

    /**
     * Revenue per day (paise) for in-flight (not-yet-settled) orders since a given
     * instant — used for the Earnings page's "Balance" sparkline.
     */
    @Query(value = """
            SELECT DATE(b.created_at) AS day,
                   COALESCE(SUM(CAST(b.total_amount AS BIGINT)), 0) AS revenuePaise
            FROM bookings b
            WHERE b.shop_id = :shopId
              AND b.status IN ('CONFIRMED', 'PROCESSING', 'OUT_FOR_DELIVERY')
              AND b.created_at >= :since
            GROUP BY DATE(b.created_at)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> dailyPendingRevenue(
            @Param("shopId") UUID shopId,
            @Param("since")  Instant since);

    /** Sum of DELIVERED (settled) revenue within a time window — for the Earnings page's weekly delta. */
    @Query("""
            SELECT COALESCE(SUM(CAST(b.totalAmount AS long)), 0)
            FROM Booking b
            WHERE b.shopId = :shopId
              AND b.status = 'DELIVERED'
              AND b.createdAt >= :from
              AND b.createdAt < :to
            """)
    long sumDeliveredForPeriod(
            @Param("shopId") UUID shopId,
            @Param("from")   Instant from,
            @Param("to")     Instant to);

    /** Sum of in-flight (not-yet-settled) revenue within a time window — for the Earnings page's "Balance" delta. */
    @Query("""
            SELECT COALESCE(SUM(CAST(b.totalAmount AS long)), 0)
            FROM Booking b
            WHERE b.shopId = :shopId
              AND b.status IN ('CONFIRMED', 'PROCESSING', 'OUT_FOR_DELIVERY')
              AND b.createdAt >= :from
              AND b.createdAt < :to
            """)
    long sumPendingForPeriod(
            @Param("shopId") UUID shopId,
            @Param("from")   Instant from,
            @Param("to")     Instant to);

    /**
     * Per-day earnings breakdown for the Earnings history table: one row per
     * (day, settlement bucket) with order count, total items sold, and revenue.
     * "PAID" = DELIVERED orders, "PENDING" = still in-flight orders.
     */
    @Query(value = """
            SELECT DATE(b.created_at) AS day,
                   CASE WHEN b.status = 'DELIVERED' THEN 'PAID' ELSE 'PENDING' END AS bucket,
                   COUNT(DISTINCT b.id) AS orderCount,
                   COALESCE(SUM(bi.quantity), 0) AS productSalesCount,
                   COALESCE(SUM(CAST(b.total_amount AS BIGINT)), 0) AS earningsPaise
            FROM bookings b
            JOIN booking_items bi ON bi.booking_id = b.id
            WHERE b.shop_id = :shopId
              AND b.status IN ('DELIVERED', 'CONFIRMED', 'PROCESSING', 'OUT_FOR_DELIVERY')
            GROUP BY DATE(b.created_at), bucket
            ORDER BY day DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT DATE(b.created_at) AS day
                FROM bookings b
                WHERE b.shop_id = :shopId
                  AND b.status IN ('DELIVERED', 'CONFIRMED', 'PROCESSING', 'OUT_FOR_DELIVERY')
                GROUP BY DATE(b.created_at), CASE WHEN b.status = 'DELIVERED' THEN 'PAID' ELSE 'PENDING' END
            ) t
            """,
            nativeQuery = true)
    Page<Object[]> dailyEarningsBreakdown(@Param("shopId") UUID shopId, Pageable pageable);

    /**
     * Per-(day, settlement bucket) payment-method counts — used to derive a "Method"
     * column for the Payouts/Statement history tables. Kept as a separate query (rather
     * than joining transactions into {@link #dailyEarningsBreakdown}) so the 1:many
     * booking_items join there isn't multiplied out by a second 1:many transactions join.
     */
    @Query(value = """
            SELECT DATE(b.created_at) AS day,
                   CASE WHEN b.status = 'DELIVERED' THEN 'PAID' ELSE 'PENDING' END AS bucket,
                   COUNT(*) FILTER (WHERE t.method = 'COD')     AS codCount,
                   COUNT(*) FILTER (WHERE t.method = 'GATEWAY') AS gatewayCount,
                   COUNT(*) FILTER (WHERE t.method = 'POINTS')  AS pointsCount
            FROM bookings b
            LEFT JOIN payments     p ON p.booking_id = b.id
            LEFT JOIN transactions t ON t.payment_id = p.id
            WHERE b.shop_id = :shopId
              AND b.status IN ('DELIVERED', 'CONFIRMED', 'PROCESSING', 'OUT_FOR_DELIVERY')
            GROUP BY DATE(b.created_at), CASE WHEN b.status = 'DELIVERED' THEN 'PAID' ELSE 'PENDING' END
            """, nativeQuery = true)
    List<Object[]> dailyPaymentModeBreakdown(@Param("shopId") UUID shopId);
}
