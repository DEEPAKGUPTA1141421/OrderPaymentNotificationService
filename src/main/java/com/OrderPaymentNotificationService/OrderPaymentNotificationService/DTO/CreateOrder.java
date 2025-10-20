package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO;

public record CreateOrder<T>(boolean success, String message, T data, int statusCode) {
}
