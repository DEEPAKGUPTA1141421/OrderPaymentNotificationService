package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoicePdf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoicePdfRepository extends JpaRepository<InvoicePdf, UUID> {
    Optional<InvoicePdf> findByInvoiceId(UUID invoiceId);
}
