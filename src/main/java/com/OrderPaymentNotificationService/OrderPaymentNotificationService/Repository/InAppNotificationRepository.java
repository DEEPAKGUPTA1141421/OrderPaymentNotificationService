package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InAppNotification;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    /**
     * Main feed query: all non-deleted notifications for user, newest first.
     */
    Page<InAppNotification> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID userId, Pageable pageable);

    /**
     * Filtered feed query: by category.
     */
    Page<InAppNotification> findByUserIdAndCategoryAndDeletedFalseOrderByCreatedAtDesc(
            UUID userId, NotificationCategory category, Pageable pageable);

    /**
     * Unread-only feed.
     */
    Page<InAppNotification> findByUserIdAndReadFalseAndDeletedFalseOrderByCreatedAtDesc(
            UUID userId, Pageable pageable);

    /**
     * Count unread (badge count on UI).
     */
    long countByUserIdAndReadFalseAndDeletedFalse(UUID userId);

    /**
     * Fetch a single non-deleted notification for ownership check.
     */
    Optional<InAppNotification> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    /**
     * Mark all unread as read for a user (read-all operation).
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE InAppNotification n
               SET n.read = true,
                   n.readAt = CURRENT_TIMESTAMP
             WHERE n.userId = :userId
               AND n.read = false
               AND n.deleted = false
            """)
    int markAllReadForUser(@Param("userId") UUID userId);

    /**
     * Soft-delete all notifications for a user (GDPR cleanup).
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE InAppNotification n
               SET n.deleted = true
             WHERE n.userId = :userId
            """)
    int softDeleteAllForUser(@Param("userId") UUID userId);
}
