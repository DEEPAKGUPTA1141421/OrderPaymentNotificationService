package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SendBirdClient {

    private final RestTemplate restTemplate;

    @Value("${sendbird.application-id}")
    private String applicationId;

    @Value("${sendbird.session-token-expires-in-seconds:604800}")
    private long sessionTokenExpiresInSeconds;

    public SendBirdClient(@Qualifier("sendBirdRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String baseUrl() {
        return "https://api-" + applicationId + ".sendbird.com/v3";
    }

    public void upsertUser(String userId, String nickname) {
        String url = baseUrl() + "/users";
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("nickname", nickname);
        body.put("profile_url", "");
        body.put("upsert", true);
        try {
            restTemplate.postForObject(url, body, Map.class);
            log.info("[SendBirdClient] Upserted user: {}", userId);
        } catch (HttpClientErrorException e) {
            log.error("[SendBirdClient] Upsert failed for {}: {} {}", userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SendBird user upsert failed for " + userId, e);
        }
    }

    public String issueSessionToken(String userId) {
        String url = baseUrl() + "/users/" + userId + "/token";
        long expiresAt = (System.currentTimeMillis() / 1000 + sessionTokenExpiresInSeconds) * 1000;
        Map<String, Object> body = Map.of("expires_at", expiresAt);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            if (response == null || !response.containsKey("token")) {
                throw new RuntimeException("No token in SendBird response for user " + userId);
            }
            return (String) response.get("token");
        } catch (HttpClientErrorException e) {
            log.error("[SendBirdClient] Token issuance failed for {}: {} {}", userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SendBird token issuance failed for " + userId, e);
        }
    }

    public String createGroupChannel(String channelName, List<String> userIds) {
        String url = baseUrl() + "/group_channels";
        Map<String, Object> body = new HashMap<>();
        body.put("name", channelName);
        body.put("channel_url", "channel_" + UUID.randomUUID().toString().replace("-", ""));
        body.put("user_ids", userIds);
        body.put("is_distinct", false);
        body.put("is_public", false);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            if (response == null || !response.containsKey("channel_url")) {
                throw new RuntimeException("No channel_url in SendBird response for channel: " + channelName);
            }
            String channelUrl = (String) response.get("channel_url");
            log.info("[SendBirdClient] Created channel: {}", channelUrl);
            return channelUrl;
        } catch (HttpClientErrorException e) {
            log.error("[SendBirdClient] Channel creation failed for {}: {} {}", channelName, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SendBird channel creation failed: " + channelName, e);
        }
    }

    public void archiveChannel(String channelUrl) {
        String url = baseUrl() + "/group_channels/" + channelUrl;
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("is_archived", true));
        try {
            restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
            log.info("[SendBirdClient] Archived channel: {}", channelUrl);
        } catch (HttpClientErrorException e) {
            log.error("[SendBirdClient] Archive failed for {}: {} {}", channelUrl, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SendBird channel archive failed: " + channelUrl, e);
        }
    }

    public void freezeChannel(String channelUrl) {
        String url = baseUrl() + "/group_channels/" + channelUrl + "/freeze";
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("freeze", true));
        try {
            restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
            log.info("[SendBirdClient] Froze channel: {}", channelUrl);
        } catch (HttpClientErrorException e) {
            log.error("[SendBirdClient] Freeze failed for {}: {} {}", channelUrl, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SendBird channel freeze failed: " + channelUrl, e);
        }
    }
}
