package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceCustomerRepository extends JpaRepository<InvoiceCustomer, UUID> {

    List<InvoiceCustomer> findBySellerIdOrderByNameAsc(UUID sellerId);

    Optional<InvoiceCustomer> findByIdAndSellerId(UUID id, UUID sellerId);

    Optional<InvoiceCustomer> findFirstBySellerIdAndPhone(UUID sellerId, String phone);

    Optional<InvoiceCustomer> findFirstBySellerIdAndEmailIgnoreCase(UUID sellerId, String email);

    Optional<InvoiceCustomer> findFirstBySellerIdAndNameIgnoreCaseAndPhoneIsNullAndEmailIsNull(UUID sellerId, String name);
}
