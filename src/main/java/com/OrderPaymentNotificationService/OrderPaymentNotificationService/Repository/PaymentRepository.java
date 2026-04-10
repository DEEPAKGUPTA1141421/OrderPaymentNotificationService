package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
     /** Find all payments for a given booking (usually 1, but supports splits). */
    List<Payment> findByBookingId(UUID bookingId);
 
    /** Convenience: first payment for a booking (most common use case). */
    Optional<Payment> findFirstByBookingId(UUID bookingId);
 
    /** Find by status – useful for background reconciliation jobs. */
    List<Payment> findByStatus(Payment.Status status);
}