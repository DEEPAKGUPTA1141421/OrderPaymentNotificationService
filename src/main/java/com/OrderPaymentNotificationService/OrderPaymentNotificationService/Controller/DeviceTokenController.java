package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.RegisterDeviceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.DeviceTokenService;

import java.util.UUID;

/**
 * Device Token APIs — manages push notification device registrations.
 *
 * All endpoints require a valid JWT (ROLE_USER).
 *
 * Security notes:
 * - userId is extracted from JWT, never from request body.
 * - Raw device tokens are NOT returned in list responses to prevent token
 * harvesting.
 * - Token re-ownership (device sold/transferred) is handled transparently.
 *
 * Endpoints:
 * POST /api/v1/users/devices — register / refresh device token
 * GET /api/v1/users/devices — list all active devices
 * DELETE /api/v1/users/devices/{id} — deregister a device (logout)
 */
@RestController
@RequestMapping("/api/v1/users/devices")
@RequiredArgsConstructor
@Validated
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    /**
     * Register or refresh a push notification device token.
     *
     * Call this on every app launch to keep the token fresh.
     * If the same token already exists for this user, metadata is updated
     * (idempotent).
     * If the token belonged to a different user (shared device), old binding is
     * revoked.
     *
     * Body example:
     * {
     * "deviceToken": "fXMkQVz9...",
     * "platform": "ANDROID",
     * "deviceName": "Rajan's OnePlus 12",
     * "appVersion": "3.2.1"
     * }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Object>> register(
            @Valid @RequestBody RegisterDeviceRequest req) {
        ApiResponse<Object> response = deviceTokenService.registerDevice(req);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    /**
     * List all active devices for the authenticated user.
     * Useful for a "Logged-in devices" management screen.
     * Raw device tokens are excluded from the response.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getDevices() {
        ApiResponse<Object> response = deviceTokenService.getDevices();
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    /**
     * Deregister a device — marks the token as inactive.
     * Called on explicit logout from a specific device.
     * Returns 404 if the device doesn't belong to the user.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deregister(@PathVariable UUID id) {
        ApiResponse<Object> response = deviceTokenService.deregisterDevice(id);
        return ResponseEntity.status(response.statusCode()).body(response);
    }
}