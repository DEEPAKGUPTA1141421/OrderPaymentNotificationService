package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.InAppNotificationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

/**
 * In-app Notification Feed APIs
 *
 * All endpoints require a valid JWT (ROLE_USER).
 * userId is extracted from the JWT — never from query params.
 *
 * Endpoints:
 * GET /api/v1/users/notifications — paginated notification feed
 * PATCH /api/v1/users/notifications/{id}/read — mark single notification as
 * read
 * PATCH /api/v1/users/notifications/read-all — mark all notifications as read
 * DELETE /api/v1/users/notifications/{id} — soft-delete a notification
 */
@RestController
@RequestMapping("/api/v1/users/notifications")
@RequiredArgsConstructor
@Validated
public class InAppNotificationController {

    private final InAppNotificationService notificationService;

    /**
     * Paginated notification feed.
     *
     * Query params:
     * page — 0-based page index (default: 0)
     * size — page size, capped at 50 (default: 20)
     * category — filter by category e.g. ORDER_UPDATES (optional)
     * unreadOnly — if true, returns only unread notifications (optional)
     *
     * Response shape:
     * {
     * "notifications": [...],
     * "unreadCount": 12,
     * "totalElements": 85,
     * "totalPages": 5,
     * "currentPage": 0
     * }
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getFeed(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean unreadOnly) {
        ApiResponse<Object> response = notificationService.getFeed(page, size, category, unreadOnly);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    /**
     * Mark a single notification as read.
     * Returns 404 if the notification doesn't belong to the user or is deleted.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Object>> markRead(@PathVariable UUID id) {
        ApiResponse<Object> response = notificationService.markRead(id);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    /**
     * Mark ALL unread notifications as read for the current user.
     * Returns the count of notifications that were updated.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Object>> markAllRead() {
        ApiResponse<Object> response = notificationService.markAllRead();
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    /**
     * Soft-delete a single notification.
     * Deleted notifications are excluded from future feed queries.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable UUID id) {
        ApiResponse<Object> response = notificationService.delete(id);
        return ResponseEntity.status(response.statusCode()).body(response);
    }
}