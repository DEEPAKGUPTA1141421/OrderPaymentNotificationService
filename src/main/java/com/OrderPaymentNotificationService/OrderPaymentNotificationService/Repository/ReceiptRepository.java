package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByBookingId(UUID bookingId);

    /** Ownership-safe lookup — used by the download endpoint. */
    Optional<Receipt> findByBookingIdAndUserId(UUID bookingId, UUID userId);

    boolean existsByBookingId(UUID bookingId);
}
