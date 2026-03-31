package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationCategory;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUserId(UUID userId);

    List<NotificationPreference> findByUserIdAndCategory(UUID userId, NotificationCategory category);

    Optional<NotificationPreference> findByUserIdAndCategoryAndChannel(
            UUID userId, NotificationCategory category, NotificationChannel channel);

    /**
     * Bulk-enable or bulk-disable all channels for a category (e.g. mute ALL
     * promotions).
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE NotificationPreference p
               SET p.enabled = :enabled
             WHERE p.userId = :userId
               AND p.category = :category
            """)
    int updateEnabledByCategoryForUser(
            @Param("userId") UUID userId,
            @Param("category") NotificationCategory category,
            @Param("enabled") boolean enabled);

    /**
     * Delete all preferences for a user (GDPR account deletion).
     */
    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);
}
