package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {
}
