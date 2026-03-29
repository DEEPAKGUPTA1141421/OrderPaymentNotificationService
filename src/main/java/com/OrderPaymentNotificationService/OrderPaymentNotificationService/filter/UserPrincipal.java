package com.OrderPaymentNotificationService.OrderPaymentNotificationService.filter;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
@Getter
public class UserPrincipal {
    private final UUID id;
    private final String phone;
    private final String role;
}
