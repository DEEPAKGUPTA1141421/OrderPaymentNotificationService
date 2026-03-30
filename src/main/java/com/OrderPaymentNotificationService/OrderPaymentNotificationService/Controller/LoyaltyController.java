package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.RedeemPointsRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.LoyaltyService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/loyalty-points")
@RequiredArgsConstructor
@Validated
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getBalance() {
        ApiResponse<Object> response = loyaltyService.getBalance();
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Object>> getHistory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        ApiResponse<Object> response = loyaltyService.getHistory(page, size);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users/loyalty-points/redeem
    // Redeem loyalty points → wallet balance or order discount
    //
    // Body example (WALLET):
    // {
    // "points": 500,
    // "destination": "WALLET",
    // "idempotencyKey": "client-uuid-xyz"
    // }
    //
    // Body example (ORDER):
    // {
    // "points": 200,
    // "destination": "ORDER",
    // "orderId": "uuid-of-order",
    // "idempotencyKey": "client-uuid-abc"
    // }
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<Object>> redeem(
            @Valid @RequestBody RedeemPointsRequest req) {
        ApiResponse<Object> response = loyaltyService.redeemPoints(req);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

}
// uiuhiuuhuiiiohkuhhuiuibjkkjkujiui kjji