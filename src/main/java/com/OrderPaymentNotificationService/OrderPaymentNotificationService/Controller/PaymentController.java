package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Payment;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.PaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/composite")
    public ResponseEntity<?> payComposite(@RequestParam UUID bookingId,
            @RequestParam String totalAmount,
            @RequestParam String pointsAmount,
            @RequestParam String pgAmount,
            @RequestParam UUID userId) {
        ApiResponse<Payment> response = paymentService.makeCompositePayment(bookingId, totalAmount, pointsAmount,
                pgAmount, userId);
        return ResponseEntity.status(response.statusCode()).body(response);
    }
}
