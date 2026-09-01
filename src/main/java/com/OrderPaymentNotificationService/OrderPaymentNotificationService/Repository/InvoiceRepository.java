package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findBySellerId(UUID sellerId, Pageable pageable);

    Page<Invoice> findBySellerIdAndStatus(UUID sellerId, Invoice.Status status, Pageable pageable);

    Optional<Invoice> findByIdAndSellerId(UUID id, UUID sellerId);

    @Query("SELECT i FROM Invoice i LEFT JOIN i.customer c WHERE i.sellerId = :sellerId AND (" +
            "LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "c.phone LIKE CONCAT('%', :q, '%'))")
    Page<Invoice> search(@Param("sellerId") UUID sellerId, @Param("q") String query, Pageable pageable);

    long countBySellerIdAndStatus(UUID sellerId, Invoice.Status status);
}
