package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.ChatMessageEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.SendBirdWebhookPayload;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat.ChatMessageProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/webhooks/sendbird")
@RequiredArgsConstructor
@Slf4j
public class SendBirdWebhookController {

    private final ChatMessageProducer chatMessageProducer;
    private final ObjectMapper objectMapper;

    @Value("${sendbird.webhook-secret:}")
    private String webhookSecret;

    /**
     * Receives SendBird message webhooks.
     * - Verifies HMAC-SHA256 signature via X-Sendbird-Signature header.
     * - Only acts on group_channel:message_send events; silently ignores others.
     * - Publishes a ChatMessageEvent to chat.message.received Kafka topic so the
     *   notification service can send FCM push to offline recipients.
     * - Always returns 200 to prevent SendBird from retrying processed events.
     */
    @PostMapping("/message")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "x-sendbird-signature", required = false) String signature,
            @RequestBody String rawBody) {

        if (!isSignatureValid(rawBody, signature)) {
            log.warn("[SendBirdWebhook] Rejected: invalid or missing signature");
            return ResponseEntity.status(401).build();
        }

        try {
            SendBirdWebhookPayload payload = objectMapper.readValue(rawBody, SendBirdWebhookPayload.class);

            if (!"group_channel:message_send".equals(payload.getCategory())) {
                return ResponseEntity.ok().build();
            }

            if (payload.getChannel() == null || payload.getSender() == null || payload.getPayload() == null) {
                log.warn("[SendBirdWebhook] Incomplete payload for category={} — skipping", payload.getCategory());
                return ResponseEntity.ok().build();
            }

            ChatMessageEvent event = ChatMessageEvent.builder()
                .channelUrl(payload.getChannel().getChannelUrl())
                .senderId(payload.getSender().getUserId())
                .messageId(payload.getPayload().getMessageId())
                .messageText(payload.getPayload().getMessage())
                .sentAt(payload.getPayload().getCreatedAt())
                .build();

            chatMessageProducer.publishMessageReceived(event);

        } catch (Exception e) {
            log.error("[SendBirdWebhook] Failed to process webhook body: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private boolean isSignatureValid(String body, String signature) {
        if (signature == null || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().equals(signature);
        } catch (Exception e) {
            log.error("[SendBirdWebhook] Signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
