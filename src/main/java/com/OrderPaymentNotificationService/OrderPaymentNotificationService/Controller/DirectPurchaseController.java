package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.BuyNow.BuyNowRequestDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.DirectPurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/buy-now")
@RequiredArgsConstructor
@Slf4j
public class DirectPurchaseController {

    private final DirectPurchaseService directPurchaseService;

    /**
     * POST /api/v1/buy-now
     *
     * Skips the cart — creates a single booking for one product/variant directly
     * from the product detail page.  The returned bookingId is then passed to
     * POST /api/v1/payment to initiate the payment gateway flow.
     *
     * Requires:  ROLE_USER JWT
     * Idempotent: No — each call creates a new booking.
     *             Frontend must NOT retry on network errors without user confirmation.
     */
    @PostMapping
    public ResponseEntity<?> buyNow(@Valid @RequestBody BuyNowRequestDto request) {
        ApiResponse<Object> response = directPurchaseService.buyNow(request);
        return ResponseEntity.status(response.statusCode()).body(response);
    }
}
