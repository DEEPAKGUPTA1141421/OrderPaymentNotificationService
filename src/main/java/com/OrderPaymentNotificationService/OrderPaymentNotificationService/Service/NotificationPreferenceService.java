package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.BulkUpdatePreferenceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.BulkUpdatePreferenceRequest.PreferenceEntry;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.NotificationPreferenceDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.NotificationDto.UpdatePreferenceRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationCategory;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationChannel;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.NotificationPreferenceRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferenceService extends BaseService {

    private final NotificationPreferenceRepository preferenceRepo;

    // ─────────────────────────────────────────────────────────────────────────
    // GET ALL PREFERENCES (GET /api/v1/users/notification-preferences)
    // Returns full matrix of (category × channel) for the current user.
    // Missing rows are auto-provisioned with sensible defaults.
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> getAll() {
        try {
            UUID userId = getUserId();
            List<NotificationPreference> stored = preferenceRepo.findByUserId(userId);

            // Auto-provision any missing (category × channel) pairs
            List<NotificationPreference> provisioned = provisionDefaults(userId, stored);

            // Group response by category for easy frontend consumption
            Map<String, List<NotificationPreferenceDto>> grouped = provisioned.stream()
                    .collect(Collectors.groupingBy(
                            p -> p.getCategory().name(),
                            LinkedHashMap::new,
                            Collectors.mapping(this::toDto, Collectors.toList())));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("preferences", grouped);
            response.put("totalCategories", NotificationCategory.values().length);
            response.put("totalChannels", NotificationChannel.values().length);

            return new ApiResponse<>(true, "Notification preferences fetched", response, 200);
        } catch (Exception e) {
            log.error("getAll preferences failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to fetch preferences", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BULK UPDATE (PUT /api/v1/users/notification-preferences)
    // Upserts multiple (category, channel) preferences at once.
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> bulkUpdate(BulkUpdatePreferenceRequest req) {
        try {
            UUID userId = getUserId();
            List<NotificationPreferenceDto> updated = new ArrayList<>();

            for (PreferenceEntry entry : req.getPreferences()) {
                NotificationCategory category = NotificationCategory.valueOf(entry.getCategory());
                NotificationChannel channel = NotificationChannel.valueOf(entry.getChannel());

                // Guard: ACCOUNT_SECURITY cannot be disabled on EMAIL or SMS (regulatory)
                if (category == NotificationCategory.ACCOUNT_SECURITY
                        && (channel == NotificationChannel.EMAIL || channel == NotificationChannel.SMS)
                        && Boolean.FALSE.equals(entry.getEnabled())) {
                    return new ApiResponse<>(false,
                            "ACCOUNT_SECURITY notifications cannot be disabled on EMAIL or SMS", null, 400);
                }

                NotificationPreference pref = preferenceRepo
                        .findByUserIdAndCategoryAndChannel(userId, category, channel)
                        .orElseGet(() -> NotificationPreference.builder()
                                .userId(userId)
                                .category(category)
                                .channel(channel)
                                .build());

                applyEntry(pref, entry);
                updated.add(toDto(preferenceRepo.save(pref)));
            }

            log.info("Bulk preference update: userId={}, count={}", userId, updated.size());
            return new ApiResponse<>(true, "Preferences updated", updated, 200);
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "Invalid category or channel: " + e.getMessage(), null, 400);
        } catch (Exception e) {
            log.error("bulkUpdate preferences failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to update preferences", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH SINGLE CATEGORY (PATCH /api/v1/users/notification-preferences/{type})
    // Updates all channels for a given category (e.g. mute all PROMOTIONS).
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> updateCategory(String categoryStr, UpdatePreferenceRequest req) {
        try {
            UUID userId = getUserId();
            NotificationCategory category;
            try {
                category = NotificationCategory.valueOf(categoryStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return new ApiResponse<>(false,
                        "Invalid category: " + categoryStr + ". Valid values: "
                                + Arrays.toString(NotificationCategory.values()),
                        null, 400);
            }

            NotificationChannel channel;
            try {
                channel = NotificationChannel.valueOf(req.getChannel().toUpperCase());
            } catch (IllegalArgumentException e) {
                return new ApiResponse<>(false,
                        "Invalid channel: " + req.getChannel(), null, 400);
            }

            // Guard: ACCOUNT_SECURITY on EMAIL/SMS cannot be disabled
            if (category == NotificationCategory.ACCOUNT_SECURITY
                    && (channel == NotificationChannel.EMAIL || channel == NotificationChannel.SMS)
                    && Boolean.FALSE.equals(req.getEnabled())) {
                return new ApiResponse<>(false,
                        "ACCOUNT_SECURITY notifications cannot be disabled on EMAIL or SMS", null, 400);
            }

            // Validate quiet-hours: if one is set, both must be set
            if ((req.getQuietStart() != null) != (req.getQuietEnd() != null)) {
                return new ApiResponse<>(false,
                        "Both quietStart and quietEnd must be provided together", null, 400);
            }

            NotificationPreference pref = preferenceRepo
                    .findByUserIdAndCategoryAndChannel(userId, category, channel)
                    .orElseGet(() -> NotificationPreference.builder()
                            .userId(userId)
                            .category(category)
                            .channel(channel)
                            .build());

            pref.setEnabled(req.getEnabled());
            if (req.getQuietStart() != null) {
                pref.setQuietStart(req.getQuietStart());
                pref.setQuietEnd(req.getQuietEnd());
            }
            if (req.getDailyCap() != null) {
                pref.setDailyCap(req.getDailyCap());
            }

            NotificationPreferenceDto dto = toDto(preferenceRepo.save(pref));
            log.info("Preference updated: userId={}, category={}, channel={}, enabled={}",
                    userId, category, channel, req.getEnabled());

            return new ApiResponse<>(true, "Preference updated", dto, 200);
        } catch (Exception e) {
            log.error("updateCategory failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to update preference", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL: check if a user wants to receive a notification
    // Called by other services (e.g. before sending Kafka event)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId, NotificationCategory category, NotificationChannel channel) {
        return preferenceRepo
                .findByUserIdAndCategoryAndChannel(userId, category, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(defaultEnabled(category)); // fall back to default if not provisioned yet
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Auto-provision the full category × channel matrix for a user.
     * Idempotent — only creates rows that don't already exist.
     */
    private List<NotificationPreference> provisionDefaults(UUID userId,
            List<NotificationPreference> existing) {
        Set<String> existingKeys = existing.stream()
                .map(p -> p.getCategory().name() + ":" + p.getChannel().name())
                .collect(Collectors.toSet());

        List<NotificationPreference> toCreate = new ArrayList<>();
        for (NotificationCategory cat : NotificationCategory.values()) {
            for (NotificationChannel ch : NotificationChannel.values()) {
                String key = cat.name() + ":" + ch.name();
                if (!existingKeys.contains(key)) {
                    toCreate.add(NotificationPreference.builder()
                            .userId(userId)
                            .category(cat)
                            .channel(ch)
                            .enabled(defaultEnabled(cat))
                            .build());
                }
            }
        }

        if (!toCreate.isEmpty()) {
            List<NotificationPreference> saved = preferenceRepo.saveAll(toCreate);
            List<NotificationPreference> combined = new ArrayList<>(existing);
            combined.addAll(saved);
            return combined;
        }
        return existing;
    }

    /**
     * Default enabled state by category.
     * Transactional & security categories default ON; marketing defaults OFF.
     */
    private boolean defaultEnabled(NotificationCategory category) {
        return switch (category) {
            case ORDER_UPDATES, PAYMENT_UPDATES, WALLET_UPDATES,
                    ACCOUNT_SECURITY, SYSTEM_ALERTS ->
                true;
            case LOYALTY_UPDATES, PRODUCT_UPDATES, REVIEW_REMINDERS -> true;
            case PROMOTIONS -> false; // opt-in for marketing
        };
    }

    private void applyEntry(NotificationPreference pref, PreferenceEntry entry) {
        pref.setEnabled(entry.getEnabled());
        if (entry.getQuietStart() != null && entry.getQuietEnd() != null) {
            pref.setQuietStart(entry.getQuietStart());
            pref.setQuietEnd(entry.getQuietEnd());
        }
        if (entry.getDailyCap() != null) {
            pref.setDailyCap(entry.getDailyCap());
        }
    }

    private NotificationPreferenceDto toDto(NotificationPreference p) {
        return NotificationPreferenceDto.builder()
                .id(p.getId())
                .category(p.getCategory().name())
                .channel(p.getChannel().name())
                .enabled(p.isEnabled())
                .quietStart(p.getQuietStart())
                .quietEnd(p.getQuietEnd())
                .dailyCap(p.getDailyCap())
                .build();
    }
}