package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.ShopStats;

import java.util.List;
import java.util.UUID;

public interface ShopStatsRepository extends JpaRepository<ShopStats, UUID> {
    List<ShopStats> findByCityAndCategory(String city, String category);
}
