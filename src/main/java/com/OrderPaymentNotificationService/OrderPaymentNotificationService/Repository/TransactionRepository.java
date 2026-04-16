package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Used by the PhonePe webhook handler to look up a COD transaction after
     * QR-based payment completion.
     * The delivery partner's QR generation stores the transaction's UUID as the
     * merchantOrderId sent to PhonePe, so the webhook payload contains this UUID.
     */
    Optional<Transaction> findByTranscationNumber(UUID transcationNumber);
}
