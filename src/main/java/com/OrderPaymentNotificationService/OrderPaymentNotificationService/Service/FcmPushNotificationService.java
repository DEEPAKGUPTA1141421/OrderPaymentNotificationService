package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.DeviceToken.Platform;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationCategory;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationChannel;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.DeviceTokenRepository;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Firebase Cloud Messaging (FCM) push notification service.
 *
 * Design notes:
 * - sendToUser() fans out to ALL active device tokens for the user.
 * - FCM errors (NotRegistered, InvalidRegistration) auto-invalidate tokens.
 * - All sends are @Async — push failures never block the calling thread.
 * - Respects the user's PUSH preference via NotificationPreferenceService.
 * - Supports both Android (data+notification) and iOS (APNS) payloads.
 * - Batch sends (up to 500 tokens per FCM multicast call) for efficiency.
 */
@Service
@Slf4j
public class FcmPushNotificationService extends BaseService {

    private final DeviceTokenRepository deviceTokenRepo;
    private final DeviceTokenService deviceTokenService;
    private final NotificationPreferenceService prefService;

    @Value("${fcm.service-account-file:classpath:firebase-service-account.json}")
    private String serviceAccountFile;

    @Value("${fcm.enabled:false}")
    private boolean fcmEnabled;

    private FirebaseMessaging firebaseMessaging;

    public FcmPushNotificationService(DeviceTokenRepository deviceTokenRepo,
            DeviceTokenService deviceTokenService,
            NotificationPreferenceService prefService) {
        this.deviceTokenRepo = deviceTokenRepo;
        this.deviceTokenService = deviceTokenService;
        this.prefService = prefService;
    }

    @PostConstruct
    public void initFirebase() {
        if (!fcmEnabled) {
            log.info("[FCM] Push notifications disabled (fcm.enabled=false). Skipping Firebase init.");
            return;
        }
        try {
            InputStream serviceAccount;
            if (serviceAccountFile.startsWith("classpath:")) {
                String path = serviceAccountFile.replace("classpath:", "");
                serviceAccount = new ClassPathResource(path).getInputStream();
            } else {
                serviceAccount = java.nio.file.Files.newInputStream(
                        java.nio.file.Path.of(serviceAccountFile));
            }

            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(serviceAccount)
                    .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            this.firebaseMessaging = FirebaseMessaging.getInstance();
            log.info("[FCM] Firebase initialized successfully.");
        } catch (Exception e) {
            log.error("[FCM] Firebase initialization failed: {}", e.getMessage());
            // Do not throw — app should still start without push
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API: send push to all devices of a user
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a push notification to all active devices of a user.
     * Respects the user's PUSH channel preference.
     * Runs asynchronously — never blocks the caller.
     *
     * @param userId   target user
     * @param category notification category (used for preference check + Android
     *                 channel)
     * @param title    notification title
     * @param body     notification body
     * @param data     optional key-value data payload (for deep-linking)
     */
    @Async
    public void sendToUser(java.util.UUID userId,
            NotificationCategory category,
            String title,
            String body,
            Map<String, String> data) {
        if (!fcmEnabled || firebaseMessaging == null) {
            log.debug("[FCM] Push skipped (disabled): userId={}, category={}", userId, category);
            return;
        }

        // Preference check
        if (!prefService.isEnabled(userId, category, NotificationChannel.PUSH)) {
            log.debug("[FCM] Push suppressed by user preference: userId={}, category={}", userId, category);
            return;
        }

        List<String> tokens = deviceTokenService.getActiveTokens(userId);
        if (tokens.isEmpty()) {
            log.debug("[FCM] No active device tokens for userId={}", userId);
            return;
        }

        sendBatch(userId, tokens, category, title, body, data != null ? data : Map.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL: batch multicast send (FCM limit = 500 per call)
    // ─────────────────────────────────────────────────────────────────────────

    private void sendBatch(java.util.UUID userId,
            List<String> tokens,
            NotificationCategory category,
            String title,
            String body,
            Map<String, String> extraData) {
        // FCM multicast limit is 500 — partition if needed
        List<List<String>> partitions = partition(tokens, 500);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (List<String> batch : partitions) {
            try {
                MulticastMessage message = buildMulticastMessage(batch, category, title, body, extraData);
                BatchResponse batchResponse = firebaseMessaging.sendEachForMulticast(message);

                successCount.addAndGet(batchResponse.getSuccessCount());
                failCount.addAndGet(batchResponse.getFailureCount());

                // Process individual send results — invalidate bad tokens
                List<SendResponse> responses = batchResponse.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    SendResponse sr = responses.get(i);
                    if (!sr.isSuccessful()) {
                        handleSendError(batch.get(i), sr.getException());
                    }
                }
            } catch (FirebaseMessagingException e) {
                log.error("[FCM] Batch send failed for userId={}: {}", userId, e.getMessage());
                failCount.addAndGet(batch.size());
            }
        }

        log.info("[FCM] Push sent: userId={}, category={}, success={}, failed={}",
                userId, category, successCount.get(), failCount.get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Message builder — separate Android / APNS configs
    // ─────────────────────────────────────────────────────────────────────────

    private MulticastMessage buildMulticastMessage(List<String> tokens,
            NotificationCategory category,
            String title,
            String body,
            Map<String, String> extraData) {
        // Shared data payload (deep-link, category, etc.)
        Map<String, String> data = new HashMap<>(extraData);
        data.put("category", category.name());
        data.put("click_action", "FLUTTER_NOTIFICATION_CLICK"); // for Flutter clients

        return MulticastMessage.builder()
                .addAllTokens(tokens)
                // Shared notification for both Android and iOS
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                // Android-specific config
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(androidPriority(category))
                        .setTtl(86_400_000) // 24h TTL in ms
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(androidChannelId(category))
                                .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                .setSound("default")
                                .build())
                        .putAllData(data)
                        .build())
                // APNS (iOS) config
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setAlert(ApsAlert.builder()
                                        .setTitle(title)
                                        .setBody(body)
                                        .build())
                                .setSound("default")
                                .setMutableContent(true) // for notification service extensions
                                .build())
                        .putAllCustomData(toObjectMap(data))
                        .build())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error handling — deactivate stale / invalid tokens automatically
    // ─────────────────────────────────────────────────────────────────────────

    private void handleSendError(String token, FirebaseMessagingException ex) {
        if (ex == null)
            return;
        MessagingErrorCode code = ex.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT) {
            log.warn("[FCM] Deactivating stale token (code={}): {}", code, maskToken(token));
            deviceTokenService.invalidateToken(token);
        } else {
            log.warn("[FCM] Send error for token {}: {} ({})", maskToken(token), ex.getMessage(), code);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Map category → Android notification channel ID.
     * These must match the channels created in the Android app.
     */
    private String androidChannelId(NotificationCategory category) {
        return switch (category) {
            case ORDER_UPDATES -> "channel_orders";
            case PAYMENT_UPDATES -> "channel_payments";
            case WALLET_UPDATES -> "channel_wallet";
            case LOYALTY_UPDATES -> "channel_loyalty";
            case ACCOUNT_SECURITY -> "channel_security";
            case PROMOTIONS -> "channel_promotions";
            default -> "channel_general";
        };
    }

    /**
     * High-priority for transactional; normal for marketing.
     */
    private AndroidConfig.Priority androidPriority(NotificationCategory category) {
        return switch (category) {
            case ORDER_UPDATES, PAYMENT_UPDATES,
                    WALLET_UPDATES, ACCOUNT_SECURITY ->
                AndroidConfig.Priority.HIGH;
            default -> AndroidConfig.Priority.NORMAL;
        };
    }

    private <V> List<List<V>> partition(List<V> list, int size) {
        List<List<V>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }

    private Map<String, Object> toObjectMap(Map<String, String> in) {
        return new HashMap<>(in);
    }

    /** Mask token for safe logging — show first 8 and last 4 chars only. */
    private String maskToken(String token) {
        if (token == null || token.length() < 12)
            return "***";
        return token.substring(0, 8) + "***" + token.substring(token.length() - 4);
    }
}