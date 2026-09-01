package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, UUID> {

    /**
     * Row-locks the counter for this seller/year so two concurrent invoice
     * finalizations can never read-increment-write the same lastValue.
     * Must be called inside a @Transactional method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM InvoiceCounter c WHERE c.sellerId = :sellerId AND c.year = :year")
    Optional<InvoiceCounter> findForUpdate(@Param("sellerId") UUID sellerId, @Param("year") int year);
}
