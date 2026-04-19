package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.ChatTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatTokenServiceTest {

    @Mock
    private SendBirdClient sendBirdClient;

    @InjectMocks
    private ChatTokenService chatTokenService;

    @Test
    void issueToken_shouldUpsertUserAndReturnToken() {
        ReflectionTestUtils.setField(chatTokenService, "sessionTokenExpiresInSeconds", 604800L);
        UUID userId = UUID.randomUUID();
        when(sendBirdClient.issueSessionToken(anyString())).thenReturn("test-session-token");

        ChatTokenResponse response = chatTokenService.issueToken(userId, "+919876543210");

        assertThat(response.getSessionToken()).isEqualTo("test-session-token");
        assertThat(response.getSendbirdUserId()).startsWith("usr_");
        assertThat(response.getSendbirdUserId()).doesNotContain("-");
        assertThat(response.getExpiresAt()).isGreaterThan(System.currentTimeMillis());
        verify(sendBirdClient).upsertUser(anyString(), eq("+919876543210"));
        verify(sendBirdClient).issueSessionToken(anyString());
    }

    @Test
    void issueToken_usesUserIdAsNicknameWhenDisplayNameIsBlank() {
        ReflectionTestUtils.setField(chatTokenService, "sessionTokenExpiresInSeconds", 604800L);
        UUID userId = UUID.randomUUID();
        when(sendBirdClient.issueSessionToken(anyString())).thenReturn("tok");

        chatTokenService.issueToken(userId, "");

        String expectedSbId = "usr_" + userId.toString().replace("-", "");
        verify(sendBirdClient).upsertUser(eq(expectedSbId), eq(expectedSbId));
    }

    @Test
    void issueToken_propagatesExceptionFromSendBirdClient() {
        ReflectionTestUtils.setField(chatTokenService, "sessionTokenExpiresInSeconds", 604800L);
        UUID userId = UUID.randomUUID();
        when(sendBirdClient.issueSessionToken(anyString())).thenThrow(new RuntimeException("SendBird unavailable"));

        assertThatThrownBy(() -> chatTokenService.issueToken(userId, "user"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("SendBird unavailable");
    }
}
