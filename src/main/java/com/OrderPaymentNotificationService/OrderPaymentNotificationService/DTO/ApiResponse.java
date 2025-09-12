package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO;

public record ApiResponse<T>(boolean success, String message, T data, int statusCode) {
}