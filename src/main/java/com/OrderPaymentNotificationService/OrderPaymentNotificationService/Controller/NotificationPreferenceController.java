package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.BulkUpdatePreferenceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.UpdatePreferenceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.NotificationPreferenceService;

/**
 * Notification Preference APIs
 *
 * Security notes:
 * - userId is ALWAYS extracted from the JWT — never from the request body.
 * - ACCOUNT_SECURITY preferences on EMAIL/SMS are non-disableable (regulatory).
 * - All endpoints require ROLE_USER JWT.
 *
 * Endpoints:
 * GET /api/v1/users/notification-preferences — fetch full preference matrix
 * PUT /api/v1/users/notification-preferences — bulk upsert preferences
 * PATCH /api/v1/users/notification-preferences/{type} — update a single
 * category+channel
 */
@RestController
@RequestMapping("/api/v1/users/notification-preferences")
@RequiredArgsConstructor
@Validated
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    /**
     * Fetch the full preference matrix for the authenticated user.
     * Auto-provisions missing rows with sensible defaults before returning.
     *
     * Response shape:
     * {
     * "preferences": {
     * "ORDER_UPDATES": [ { channel: "EMAIL", enabled: true, ... }, ... ],
     * "PROMOTIONS": [ { channel: "EMAIL", enabled: false, ... }, ... ],
     * ...
     * },
     * "totalCategories": 9,
     * "totalChannels": 4
     * }
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getAll() {
        ApiResponse<Object> response = preferenceService.getAll();
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    /**
     * Bulk upsert multiple (category, channel) preferences in one call.
     *
     * Body example:
     * {
     * "preferences": [
     * { "category": "PROMOTIONS", "channel": "EMAIL", "enabled": false },
     * { "category": "PROMOTIONS", "channel": "PUSH", "enabled": false },
     * { "category": "ORDER_UPDATES", "channel": "SMS", "enabled": true,
     * "quietStart": "22:00", "quietEnd": "08:00", "dailyCap": 5 }
     * ]
     * }
     */
    @PutMapping
    public ResponseEntity<ApiResponse<Object>> bulkUpdate(
            @Valid @RequestBody BulkUpdatePreferenceRequest req) {
        ApiResponse<Object> response = preferenceService.bulkUpdate(req);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    /**
     * Update a single (category, channel) preference.
     *
     * Path variable {type} = notification category, e.g. ORDER_UPDATES, PROMOTIONS.
     *
     * Body example:
     * {
     * "channel": "EMAIL",
     * "enabled": false,
     * "quietStart": "22:00",
     * "quietEnd": "08:00",
     * "dailyCap": 3
     * }
     */
    @PatchMapping("/{type}")
    public ResponseEntity<ApiResponse<Object>> updateCategory(
            @PathVariable String type,
            @Valid @RequestBody UpdatePreferenceRequest req) {
        ApiResponse<Object> response = preferenceService.updateCategory(type, req);
        return ResponseEntity.status(response.statusCode()).body(response);
    }
}