package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.ChatMessageEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat.ChatMessageProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendBirdWebhookControllerTest {

    @Mock private ChatMessageProducer chatMessageProducer;

    @InjectMocks
    private SendBirdWebhookController controller;

    private static final String SECRET = "test-webhook-secret";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(controller, "objectMapper", objectMapper);
    }

    @Test
    void handleWebhook_publishesEventForMessageSendCategory() throws Exception {
        String body = """
            {
              "category": "group_channel:message_send",
              "channel": {"channel_url": "channel_abc", "name": "test"},
              "sender": {"user_id": "usr_sender"},
              "payload": {"message_id": 42, "message": "Hello", "created_at": 1000000}
            }
            """;

        String sig = computeHmac(body, SECRET);
        ResponseEntity<Void> response = controller.handleWebhook(sig, body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<ChatMessageEvent> captor = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(chatMessageProducer).publishMessageReceived(captor.capture());
        ChatMessageEvent event = captor.getValue();
        assertThat(event.getChannelUrl()).isEqualTo("channel_abc");
        assertThat(event.getSenderId()).isEqualTo("usr_sender");
        assertThat(event.getMessageId()).isEqualTo(42L);
        assertThat(event.getMessageText()).isEqualTo("Hello");
    }

    @Test
    void handleWebhook_returns401ForInvalidSignature() {
        String body = "{\"category\":\"group_channel:message_send\"}";
        ResponseEntity<Void> response = controller.handleWebhook("bad-signature", body);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(chatMessageProducer);
    }

    @Test
    void handleWebhook_ignoresNonMessageCategories() throws Exception {
        String body = "{\"category\":\"group_channel:message_delete\"}";
        String sig = computeHmac(body, SECRET);

        ResponseEntity<Void> response = controller.handleWebhook(sig, body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(chatMessageProducer);
    }

    private String computeHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
