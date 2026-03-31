package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.DeviceToken;
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
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    List<DeviceToken> findByUserIdAndActiveTrue(UUID userId);

    Optional<DeviceToken> findByDeviceToken(String deviceToken);

    Optional<DeviceToken> findByDeviceTokenAndActiveTrue(String deviceToken);

    /**
     * Deactivate all tokens for a user (logout-all-devices).
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE DeviceToken d
               SET d.active = false
             WHERE d.userId = :userId
            """)
    int deactivateAllForUser(@Param("userId") UUID userId);

    /**
     * Deactivate a specific token (single-device logout).
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE DeviceToken d
               SET d.active = false
             WHERE d.deviceToken = :token
            """)
    int deactivateToken(@Param("token") String token);

    long countByUserIdAndActiveTrue(UUID userId);
}
