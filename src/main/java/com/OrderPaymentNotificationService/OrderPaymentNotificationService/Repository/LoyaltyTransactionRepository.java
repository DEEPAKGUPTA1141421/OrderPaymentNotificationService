package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.LoyaltyTransaction;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {

    Page<LoyaltyTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<LoyaltyTransaction> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            SELECT COALESCE(SUM(lt.points), 0)
            FROM LoyaltyTransaction lt
            WHERE lt.userId = :userId
              AND lt.type = 'REDEEM'
              AND lt.createdAt >= :since
            """)
    Long sumRedeemedSince(@Param("userId") UUID userId,
            @Param("since") ZonedDateTime since);

    boolean existsByUserIdAndSource(UUID userId, LoyaltyTransaction.TxSource source);
}
