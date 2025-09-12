package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.BookingService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.annotation.PrivateApi;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @GetMapping("/checkout")
    @PrivateApi
    public ResponseEntity<?> checkout() {
        ApiResponse<Object> response = bookingService.createBookingFromCart();
        return ResponseEntity.status(response.statusCode()).body(response);
    }

}
