package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SavedPaymentMethod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, UUID> {

    List<SavedPaymentMethod> findByUserIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(UUID userId);

    Optional<SavedPaymentMethod> findByIdAndUserId(UUID id, UUID userId);

    Optional<SavedPaymentMethod> findByUserIdAndGatewayToken(UUID userId, String gatewayToken);

    Optional<SavedPaymentMethod> findByUserIdAndUpiId(UUID userId, String upiId);

    long countByUserIdAndActiveTrue(UUID userId);

    /**
     * Clears the default flag on all methods for a user before setting a new one
     */
    @Query("UPDATE SavedPaymentMethod s SET s.isDefault = false WHERE s.userId = :userId")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void clearDefaultForUser(@Param("userId") UUID userId);
}