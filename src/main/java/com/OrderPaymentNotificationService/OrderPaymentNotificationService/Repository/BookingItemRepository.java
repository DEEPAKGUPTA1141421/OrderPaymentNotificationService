package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.BookingItem;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {
}
