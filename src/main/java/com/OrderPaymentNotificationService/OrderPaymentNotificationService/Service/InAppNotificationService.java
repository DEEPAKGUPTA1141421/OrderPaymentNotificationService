package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.InAppNotificationDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.InAppNotification;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationCategory;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationChannel;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.InAppNotificationRepository;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationService extends BaseService {

    private final InAppNotificationRepository notifRepo;
    private final NotificationPreferenceService prefService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET NOTIFICATION FEED (GET /api/v1/users/notifications)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Object> getFeed(int page, int size, String categoryFilter, Boolean unreadOnly) {
        try {
            UUID userId = getUserId();
            int safeSize = Math.min(Math.max(size, 1), 50);
            Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

            Page<InAppNotification> notifPage;

            if (unreadOnly != null && unreadOnly) {
                // Unread-only feed
                notifPage = notifRepo.findByUserIdAndReadFalseAndDeletedFalseOrderByCreatedAtDesc(
                        userId, pageable);
            } else if (categoryFilter != null && !categoryFilter.isBlank()) {
                // Category-filtered feed
                NotificationCategory category;
                try {
                    category = NotificationCategory.valueOf(categoryFilter.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return new ApiResponse<>(false, "Invalid category filter: " + categoryFilter, null, 400);
                }
                notifPage = notifRepo.findByUserIdAndCategoryAndDeletedFalseOrderByCreatedAtDesc(
                        userId, category, pageable);
            } else {
                // Full feed
                notifPage = notifRepo.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(
                        userId, pageable);
            }

            long unreadCount = notifRepo.countByUserIdAndReadFalseAndDeletedFalse(userId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("notifications", notifPage.getContent().stream().map(this::toDto).toList());
            response.put("unreadCount", unreadCount);
            response.put("totalElements", notifPage.getTotalElements());
            response.put("totalPages", notifPage.getTotalPages());
            response.put("currentPage", notifPage.getNumber());

            return new ApiResponse<>(true, "Notifications fetched", response, 200);
        } catch (Exception e) {
            log.error("getFeed failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to fetch notifications", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK AS READ (PATCH /api/v1/users/notifications/{id}/read)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> markRead(UUID notifId) {
        try {
            UUID userId = getUserId();
            InAppNotification notif = notifRepo.findByIdAndUserIdAndDeletedFalse(notifId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

            if (notif.isRead()) {
                return new ApiResponse<>(true, "Notification already read", toDto(notif), 200);
            }

            notif.setRead(true);
            notif.setReadAt(ZonedDateTime.now(ZoneId.of("Asia/Kolkata")));
            notifRepo.save(notif);

            return new ApiResponse<>(true, "Notification marked as read", toDto(notif), 200);
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 404);
        } catch (Exception e) {
            log.error("markRead failed for notifId={}: {}", notifId, e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to mark notification as read", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK ALL AS READ (PATCH /api/v1/users/notifications/read-all)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> markAllRead() {
        try {
            int count = notifRepo.markAllReadForUser(getUserId());
            log.info("markAllRead: userId={}, count={}", getUserId(), count);
            return new ApiResponse<>(true, count + " notification(s) marked as read",
                    Map.of("markedCount", count), 200);
        } catch (Exception e) {
            log.error("markAllRead failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to mark all as read", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE (DELETE /api/v1/users/notifications/{id})
    // Soft-delete to preserve audit trail
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> delete(UUID notifId) {
        try {
            UUID userId = getUserId();
            InAppNotification notif = notifRepo.findByIdAndUserIdAndDeletedFalse(notifId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

            notif.setDeleted(true);
            notifRepo.save(notif);

            log.info("Notification soft-deleted: id={}, user={}", notifId, userId);
            return new ApiResponse<>(true, "Notification deleted", null, 200);
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 404);
        } catch (Exception e) {
            log.error("delete notification failed: notifId={}, error={}", notifId, e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to delete notification", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL: publish a new in-app notification
    // Called by other services (OrderService, WalletService, etc.)
    // Respects the user's IN_APP preference for the given category.
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public InAppNotification publish(UUID userId,
            NotificationCategory category,
            String title,
            String body,
            String actionUrl,
            String referenceId) {
        try {
            // Respect preference opt-out
            if (!prefService.isEnabled(userId, category, NotificationChannel.IN_APP)) {
                log.debug("IN_APP notification suppressed for userId={}, category={}", userId, category);
                return null;
            }

            InAppNotification notif = InAppNotification.builder()
                    .userId(userId)
                    .category(category)
                    .title(title)
                    .body(body)
                    .actionUrl(actionUrl)
                    .referenceId(referenceId)
                    .build();

            InAppNotification saved = notifRepo.save(notif);
            log.info("IN_APP notification published: userId={}, category={}, id={}", userId, category, saved.getId());
            return saved;
        } catch (Exception e) {
            // Never let notification failure break the calling service
            log.error("Failed to publish in-app notification: userId={}, category={}, error={}",
                    userId, category, e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private InAppNotificationDto toDto(InAppNotification n) {
        return InAppNotificationDto.builder()
                .id(n.getId())
                .category(n.getCategory().name())
                .title(n.getTitle())
                .body(n.getBody())
                .actionUrl(n.getActionUrl())
                .imageUrl(n.getImageUrl())
                .referenceId(n.getReferenceId())
                .read(n.isRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}