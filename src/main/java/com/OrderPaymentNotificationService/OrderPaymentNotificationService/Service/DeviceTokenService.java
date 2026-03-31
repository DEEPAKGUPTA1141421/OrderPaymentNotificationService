package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.DeviceTokenDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.RegisterDeviceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.DeviceToken;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.DeviceToken.Platform;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.DeviceTokenRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenService extends BaseService {

    private final DeviceTokenRepository deviceTokenRepo;

    /** Maximum active devices per user (prevent abuse). */
    private static final long MAX_DEVICES_PER_USER = 10L;

    // ─────────────────────────────────────────────────────────────────────────
    // REGISTER DEVICE (POST /api/v1/users/devices)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> registerDevice(RegisterDeviceRequest req) {
        try {
            UUID userId = getUserId();

            Platform platform;
            try {
                platform = Platform.valueOf(req.getPlatform().toUpperCase());
            } catch (IllegalArgumentException e) {
                return new ApiResponse<>(false, "Invalid platform: " + req.getPlatform(), null, 400);
            }

            // Case 1: Token already exists in our system
            Optional<DeviceToken> existing = deviceTokenRepo.findByDeviceToken(req.getDeviceToken());
            if (existing.isPresent()) {
                DeviceToken token = existing.get();

                // Re-ownership: token moved to a different user (device sold/shared)
                if (!token.getUserId().equals(userId)) {
                    log.warn("Device token re-registration: old userId={}, new userId={}",
                            token.getUserId(), userId);
                    token.setActive(false);
                    deviceTokenRepo.save(token);
                    // Fall through to create new binding
                } else if (!token.isActive()) {
                    // Same user, token was deactivated — reactivate it
                    token.setActive(true);
                    token.setDeviceName(req.getDeviceName());
                    token.setAppVersion(req.getAppVersion());
                    DeviceToken saved = deviceTokenRepo.save(token);
                    log.info("Device token reactivated: userId={}, tokenId={}", userId, saved.getId());
                    return new ApiResponse<>(true, "Device registered", toDto(saved), 200);
                } else {
                    // Already active for the same user — idempotent, update metadata
                    token.setDeviceName(req.getDeviceName());
                    token.setAppVersion(req.getAppVersion());
                    DeviceToken saved = deviceTokenRepo.save(token);
                    return new ApiResponse<>(true, "Device already registered", toDto(saved), 200);
                }
            }

            // Case 2: Cap check
            if (deviceTokenRepo.countByUserIdAndActiveTrue(userId) >= MAX_DEVICES_PER_USER) {
                return new ApiResponse<>(false,
                        "Maximum " + MAX_DEVICES_PER_USER + " devices allowed per account. "
                                + "Please remove an existing device first.",
                        null, 400);
            }

            // Case 3: New token
            DeviceToken token = DeviceToken.builder()
                    .userId(userId)
                    .deviceToken(req.getDeviceToken())
                    .platform(platform)
                    .deviceName(req.getDeviceName())
                    .appVersion(req.getAppVersion())
                    .active(true)
                    .build();

            DeviceToken saved = deviceTokenRepo.save(token);
            log.info("Device registered: userId={}, platform={}, tokenId={}", userId, platform, saved.getId());
            return new ApiResponse<>(true, "Device registered successfully", toDto(saved), 201);

        } catch (Exception e) {
            log.error("registerDevice failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to register device", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET USER DEVICES (GET /api/v1/users/devices)
    // Returns list of active devices — useful for device-management UI.
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Object> getDevices() {
        try {
            List<DeviceTokenDto> devices = deviceTokenRepo
                    .findByUserIdAndActiveTrue(getUserId())
                    .stream()
                    .map(this::toDto)
                    .toList();
            return new ApiResponse<>(true, "Devices fetched", devices, 200);
        } catch (Exception e) {
            log.error("getDevices failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to fetch devices", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEREGISTER A TOKEN (DELETE /api/v1/users/devices/{id})
    // Used on logout or when user manually removes a device.
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> deregisterDevice(UUID deviceId) {
        try {
            DeviceToken token = deviceTokenRepo.findById(deviceId)
                    .filter(d -> d.getUserId().equals(getUserId()))
                    .orElseThrow(() -> new IllegalArgumentException("Device not found"));

            token.setActive(false);
            deviceTokenRepo.save(token);

            log.info("Device deregistered: userId={}, deviceId={}", getUserId(), deviceId);
            return new ApiResponse<>(true, "Device removed", null, 200);
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 404);
        } catch (Exception e) {
            log.error("deregisterDevice failed: deviceId={}, error={}", deviceId, e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to remove device", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL: fetch active tokens for sending push notifications
    // ─────────────────────────────────────────────────────────────────────────

    public List<String> getActiveTokens(UUID userId) {
        return deviceTokenRepo.findByUserIdAndActiveTrue(userId)
                .stream()
                .map(DeviceToken::getDeviceToken)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL: invalidate a token that FCM reports as expired/invalid
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void invalidateToken(String rawToken) {
        deviceTokenRepo.deactivateToken(rawToken);
        log.info("Device token invalidated by FCM feedback: {}", rawToken);
    }

    private DeviceTokenDto toDto(DeviceToken d) {
        return DeviceTokenDto.builder()
                .id(d.getId())
                .platform(d.getPlatform().name())
                .deviceName(d.getDeviceName())
                .appVersion(d.getAppVersion())
                .active(d.isActive())
                .createdAt(d.getCreatedAt())
                .build();
    }
}