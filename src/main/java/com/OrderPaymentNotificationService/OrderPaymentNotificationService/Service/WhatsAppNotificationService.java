package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends an invoice PDF over WhatsApp via the Meta Cloud API (Graph API v19+):
 *   1. Upload the PDF to /{phoneNumberId}/media → get a media id
 *   2. Send a "document" message referencing that media id
 *
 * Requires WHATSAPP_PHONE_NUMBER_ID + WHATSAPP_ACCESS_TOKEN to be configured
 * (a Meta Business/WhatsApp Cloud API account). Until those are supplied this
 * throws — InvoiceDeliveryConsumerService catches that and records the
 * InvoiceDelivery as FAILED with the reason, rather than silently no-op'ing.
 */
@Service("whatsappNotificationService")
@Slf4j
public class WhatsAppNotificationService implements NotificationService {

    @Value("${whatsapp.enabled}")
    private boolean enabled;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Value("${whatsapp.api.base-url:https://graph.facebook.com/v19.0}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendNotification(String to, String subject, String message, File attachment) {
        if (!enabled || phoneNumberId.isBlank() || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "WhatsApp delivery is not configured — set WHATSAPP_ENABLED=true, " +
                    "WHATSAPP_PHONE_NUMBER_ID and WHATSAPP_ACCESS_TOKEN to enable it.");
        }
        String mediaId = uploadMedia(attachment);
        sendDocumentMessage(to, mediaId, attachment.getName(), subject);
    }

    private String uploadMedia(File file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("messaging_product", "whatsapp");
            body.add("type", "application/pdf");
            body.add("file", new FileSystemResource(file));

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            Map<?, ?> response = restTemplate.postForObject(
                    baseUrl + "/" + phoneNumberId + "/media", entity, Map.class);

            Object id = response != null ? response.get("id") : null;
            if (id == null) throw new IllegalStateException("WhatsApp media upload returned no id");
            return id.toString();
        } catch (Exception e) {
            log.error("[WhatsApp] Media upload failed", e);
            throw new RuntimeException("WhatsApp media upload failed: " + e.getMessage(), e);
        }
    }

    private void sendDocumentMessage(String to, String mediaId, String filename, String caption) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> document = new HashMap<>();
            document.put("id", mediaId);
            document.put("filename", filename);
            if (caption != null && !caption.isBlank()) document.put("caption", caption);

            Map<String, Object> body = new HashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", normalizePhone(to));
            body.put("type", "document");
            body.put("document", document);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForObject(baseUrl + "/" + phoneNumberId + "/messages", entity, Map.class);
            log.info("[WhatsApp] Invoice document sent | to={}", mask(to));
        } catch (Exception e) {
            log.error("[WhatsApp] Send message failed", e);
            throw new RuntimeException("WhatsApp send failed: " + e.getMessage(), e);
        }
    }

    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9+]", "");
        return digits.startsWith("+") ? digits.substring(1) : digits;
    }

    private String mask(String s) {
        return s.length() <= 4 ? "***" : s.substring(0, 2) + "***" + s.substring(s.length() - 2);
    }
}
