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
    Object[] sumOrdersAndRevenueForPeriod(
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
}
