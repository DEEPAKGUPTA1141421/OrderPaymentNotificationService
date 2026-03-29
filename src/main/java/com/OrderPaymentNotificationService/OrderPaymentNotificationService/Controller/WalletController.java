package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.AddMoneyRequestDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.WalletService;

import java.util.UUID;

/**
 * Wallet & Money APIs
 *
 * All endpoints require a valid JWT (ROLE_USER).
 *
 * Security notes:
 * - userId is ALWAYS taken from the JWT, never from the request body.
 * This prevents horizontal privilege escalation (user A accessing user B's
 * wallet).
 * - Rate limiting is enforced at the service layer (daily cap, per-tx cap).
 * - Payment amounts are validated with @DecimalMin / @DecimalMax.
 * - Idempotency keys prevent duplicate top-ups on network retries.
 */
@RestController
@RequestMapping("/api/v1/users/wallet")
@RequiredArgsConstructor
@Validated
public class WalletController {
    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getWallet() {
        ApiResponse<Object> response = walletService.getWallet();
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    @PostMapping("/add-money")
    public ResponseEntity<ApiResponse<Object>> addMoney(
            @Valid @RequestBody AddMoneyRequestDto dto) {
        ApiResponse<Object> response = walletService.addMoney(dto);
        return ResponseEntity.status(response.statusCode()).body(response);
    }
}
