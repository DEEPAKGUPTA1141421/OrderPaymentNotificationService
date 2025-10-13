package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthHeaderInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        String authHeader = RequestContext.getAuthHeader();
        if (authHeader != null) {
            template.header("Authorization", authHeader);
        }
    }
}
