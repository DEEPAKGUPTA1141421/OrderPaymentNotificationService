package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils;

public class RequestContext {
    private static final ThreadLocal<String> authHeader = new ThreadLocal<>();

    public static void setAuthHeader(String header) {
        authHeader.set(header);
    }

    public static String getAuthHeader() {
        return authHeader.get();
    }

    public static void clear() {
        authHeader.remove();
    }
}
