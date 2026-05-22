package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.BookingItem;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {

    /** Top N products by revenue for a seller's shop across confirmed+ orders. */
    @Query(value = """
            SELECT
                bi.product_id                                        AS productId,
                bi.product_name                                      AS productName,
                SUM(bi.quantity)                                     AS totalQty,
                SUM(CAST(bi.price AS BIGINT) * bi.quantity)         AS totalRevenuePaise
            FROM booking_items bi
            JOIN bookings b ON bi.booking_id = b.id
            WHERE b.shop_id = :shopId
              AND b.status IN ('CONFIRMED','PROCESSING','OUT_FOR_DELIVERY','DELIVERED')
            GROUP BY bi.product_id, bi.product_name
            ORDER BY totalRevenuePaise DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topProductsByRevenue(
            @Param("shopId") UUID shopId,
            @Param("limit")  int  limit);
}
