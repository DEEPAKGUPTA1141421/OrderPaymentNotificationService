package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Paginated list of all bookings for a given user. Sorted via Pageable. */
    Page<Booking> findByUserId(UUID userId, Pageable pageable);
}
