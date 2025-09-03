package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SponsoredSection;

@Repository
public interface SponsoredSectionRepository extends JpaRepository<SponsoredSection, UUID> {

    @Query("SELECT s FROM SponsoredSection s WHERE s.active = true AND CURRENT_DATE BETWEEN s.startDate AND s.endDate ORDER BY s.startDate DESC")
    List<SponsoredSection> findActiveSponsoredSections();
}
