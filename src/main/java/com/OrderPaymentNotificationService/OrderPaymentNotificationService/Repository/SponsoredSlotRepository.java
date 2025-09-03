package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SponsoredSlot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SponsoredSlotRepository extends JpaRepository<SponsoredSlot, UUID> {

    List<SponsoredSlot> findByActiveTrueAndEndDateAfter(LocalDateTime now);

    List<SponsoredSlot> findByPageAndActiveTrueAndEndDateAfter(SponsoredSlot.Page page, LocalDateTime now);
}
