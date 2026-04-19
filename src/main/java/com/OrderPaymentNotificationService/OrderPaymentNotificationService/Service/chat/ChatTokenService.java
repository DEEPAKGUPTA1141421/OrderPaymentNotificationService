package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.ChatTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatTokenService {

    private final SendBirdClient sendBirdClient;

    @Value("${sendbird.session-token-expires-in-seconds:604800}")
    private long sessionTokenExpiresInSeconds;

    public ChatTokenResponse issueToken(UUID userId, String displayName) {
        String sbUserId = "usr_" + userId.toString().replace("-", "");
        String nickname = (displayName != null && !displayName.isBlank()) ? displayName : sbUserId;

        sendBirdClient.upsertUser(sbUserId, nickname);
        String token = sendBirdClient.issueSessionToken(sbUserId);
        long expiresAt = (System.currentTimeMillis() / 1000 + sessionTokenExpiresInSeconds) * 1000;

        log.info("[ChatTokenService] Issued token for userId={}", userId);
        return ChatTokenResponse.builder()
            .sendbirdUserId(sbUserId)
            .sessionToken(token)
            .expiresAt(expiresAt)
            .build();
    }
}
