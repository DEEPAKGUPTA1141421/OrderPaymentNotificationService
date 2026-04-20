package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.ChatTokenResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.SupportChannelResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.chat.SupportTicketRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat.ChatChannelService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.chat.ChatTokenService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.filter.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatTokenService chatTokenService;
    private final ChatChannelService chatChannelService;

    /**
     * Issues a SendBird session token for the authenticated user.
     * Creates the SendBird user via upsert if they don't exist yet.
     * Safe to call on every login — idempotent.
     */
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<ChatTokenResponse>> issueToken() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder
            .getContext().getAuthentication().getPrincipal();

        ChatTokenResponse data = chatTokenService.issueToken(principal.getId(), principal.getPhone());
        return ResponseEntity.ok(new ApiResponse<>(true, "Token issued", data, 200));
    }

    /**
     * Creates a SendBird support channel linking the customer to a support agent.
     * ticketId must be unique per ticket — re-calling with the same ticketId is idempotent.
     */
    @PostMapping("/support/ticket")
    public ResponseEntity<ApiResponse<SupportChannelResponse>> createSupportChannel(
            @Valid @RequestBody SupportTicketRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder
            .getContext().getAuthentication().getPrincipal();

        String channelUrl = chatChannelService.createSupportChannel(
            request.getTicketId(), principal.getId(), null);

        SupportChannelResponse data = SupportChannelResponse.builder()
            .channelUrl(channelUrl)
            .ticketId(request.getTicketId())
            .build();

        return ResponseEntity.ok(new ApiResponse<>(true, "Support channel created", data, 200));
    }
}
