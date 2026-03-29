package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    List<WalletTransaction> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            SELECT COALESCE(SUM(wt.amountPaise), 0)
            FROM WalletTransaction wt
            WHERE wt.userId = :userId
              AND wt.type = 'CREDIT'
              AND wt.source = 'TOP_UP'
              AND wt.status = 'SUCCESS'
              AND wt.createdAt >= :since
            """)
    Long sumTopUpAmountSince(@Param("userId") UUID userId,
            @Param("since") ZonedDateTime since);
}