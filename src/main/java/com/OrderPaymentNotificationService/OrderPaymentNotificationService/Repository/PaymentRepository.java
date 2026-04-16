package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Find all payments for a given booking (usually 1, but supports splits). */
    List<Payment> findByBookingId(UUID bookingId);

    /** Convenience: first payment for a booking (most common use case). */
    Optional<Payment> findFirstByBookingId(UUID bookingId);

    /** Find the successful payment for a booking — used for receipt generation. */
    Optional<Payment> findFirstByBookingIdAndStatus(UUID bookingId, Payment.Status status);

    /** Find by status – useful for background reconciliation jobs. */
    List<Payment> findByStatus(Payment.Status status);

    /**
     * Batch-fetch payments with their transactions for a set of booking IDs.
     * A single JOIN FETCH query — avoids N+1 on the order-list page.
     */
    @Query("SELECT DISTINCT p FROM Payment p LEFT JOIN FETCH p.transactions WHERE p.bookingId IN :bookingIds")
    List<Payment> findAllWithTransactionsByBookingIdIn(@Param("bookingIds") Collection<UUID> bookingIds);
}