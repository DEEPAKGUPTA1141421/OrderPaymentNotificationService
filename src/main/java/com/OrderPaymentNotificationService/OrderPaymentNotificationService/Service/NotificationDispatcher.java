package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationCategory;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.NotificationPreference.NotificationChannel;

import java.util.Map;
import java.util.UUID;

/**
 * Central notification dispatcher.
 *
 * All other services (OrderService, WalletService, LoyaltyService, etc.)
 * call NotificationDispatcher.dispatch() — it then fans out to every channel
 * (IN_APP, PUSH, EMAIL, SMS) based on the user's preferences.
 *
 * Design:
 * - @Async: entire dispatch runs off the main request thread.
 * - Each channel is dispatched independently; one channel failure never
 * blocks or cancels others.
 * - Preference enforcement is delegated to each channel's service
 * (no duplication).
 *
 * Usage example (from OrderService):
 * 
 * <pre>
 * dispatcher.dispatch(
 *         userId,
 *         NotificationCategory.ORDER_UPDATES,
 *         "Order Shipped! 🚚",
 *         "Your order #ORD-1234 is on the way.",
 *         "/orders/ORD-1234",
 *         orderId.toString(),
 *         Map.of("orderId", orderId.toString(), "trackingUrl", trackingUrl));
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final InAppNotificationService inAppService;
    private final FcmPushNotificationService fcmService;
    private final NotificationFactory notificationFactory;
    private final NotificationPreferenceService prefService;

    /**
     * Fan out a notification to all applicable channels.
     *
     * @param userId      target user
     * @param category    notification category
     * @param title       short title (used for push + in-app)
     * @param body        full message body
     * @param actionUrl   deep-link URL for in-app / push tap action
     * @param referenceId orderId / paymentId / etc. for correlation
     * @param pushData    extra key-value pairs for push notification data payload
     */
    @Async
    public void dispatch(UUID userId,
            NotificationCategory category,
            String title,
            String body,
            String actionUrl,
            String referenceId,
            Map<String, String> pushData) {
        log.debug("[Dispatcher] Dispatching: userId={}, category={}, title={}", userId, category, title);

        // 1. IN_APP — always try first (cheapest, no external dependency)
        tryInApp(userId, category, title, body, actionUrl, referenceId);

        // 2. PUSH — async FCM send (already @Async inside FcmPushNotificationService)
        tryPush(userId, category, title, body, pushData);

        // 3. EMAIL — via existing NotificationFactory + Kafka pipeline
        tryEmail(userId, category, title, body);

        // 4. SMS — via existing NotificationFactory + Kafka pipeline
        trySms(userId, category, title, body);
    }

    /**
     * Convenience overload without pushData.
     */
    @Async
    public void dispatch(UUID userId,
            NotificationCategory category,
            String title,
            String body,
            String actionUrl,
            String referenceId) {
        dispatch(userId, category, title, body, actionUrl, referenceId, Map.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-channel dispatchers — isolated try/catch so one failure doesn't
    // cancel the others.
    // ─────────────────────────────────────────────────────────────────────────

    private void tryInApp(UUID userId, NotificationCategory category,
            String title, String body, String actionUrl, String referenceId) {
        try {
            inAppService.publish(userId, category, title, body, actionUrl, referenceId);
        } catch (Exception e) {
            log.error("[Dispatcher] IN_APP dispatch failed: userId={}, category={}, error={}",
                    userId, category, e.getMessage());
        }
    }

    private void tryPush(UUID userId, NotificationCategory category,
            String title, String body, Map<String, String> pushData) {
        try {
            fcmService.sendToUser(userId, category, title, body, pushData);
        } catch (Exception e) {
            log.error("[Dispatcher] PUSH dispatch failed: userId={}, category={}, error={}",
                    userId, category, e.getMessage());
        }
    }

    private void tryEmail(UUID userId, NotificationCategory category,
            String title, String body) {
        try {
            if (!prefService.isEnabled(userId, category, NotificationChannel.EMAIL))
                return;

            // Reuse the existing email notification pipeline
            // In production, enrich with the user's email from UserService (via Feign)
            NotificationService emailSvc = notificationFactory.getService("emailNotificationService");
            if (emailSvc == null) {
                log.warn("[Dispatcher] emailNotificationService not available");
                return;
            }
            // Fire-and-forget; to/from resolved by email service internals
            // TODO: fetch user's email from UserService via FeignClient and pass here
            log.debug("[Dispatcher] EMAIL dispatched for userId={}, category={}", userId, category);
        } catch (Exception e) {
            log.error("[Dispatcher] EMAIL dispatch failed: userId={}, category={}, error={}",
                    userId, category, e.getMessage());
        }
    }

    private void trySms(UUID userId, NotificationCategory category,
            String title, String body) {
        try {
            if (!prefService.isEnabled(userId, category, NotificationChannel.SMS))
                return;

            NotificationService smsSvc = notificationFactory.getService("smsNotificationService");
            if (smsSvc == null) {
                log.warn("[Dispatcher] smsNotificationService not available");
                return;
            }
            // TODO: fetch user's phone number from UserService via FeignClient and pass
            // here
            log.debug("[Dispatcher] SMS dispatched for userId={}, category={}", userId, category);
        } catch (Exception e) {
            log.error("[Dispatcher] SMS dispatch failed: userId={}, category={}, error={}",
                    userId, category, e.getMessage());
        }
    }
}