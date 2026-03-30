package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.SaveCardRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.SaveUpiRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.SavedPaymentMethodService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {
    private final SavedPaymentMethodService savedPaymentMethodService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getAll() {
        ApiResponse<Object> response = savedPaymentMethodService.getAll();
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    @PostMapping("/card")
    public ResponseEntity<ApiResponse<Object>> saveCard(
            @Valid @RequestBody SaveCardRequest req) {
        ApiResponse<Object> response = savedPaymentMethodService.saveCard(req);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    @PostMapping("/upi")
    public ResponseEntity<ApiResponse<Object>> saveUpi(
            @Valid @RequestBody SaveUpiRequest req) {
        ApiResponse<Object> response = savedPaymentMethodService.saveUpi(req);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable UUID id) {
        ApiResponse<Object> response = savedPaymentMethodService.delete(id);
        return ResponseEntity.status(response.statusCode()).body(response);
    }

}
// uouiu8u8oijlioijijojlkhkuuhbmjhkjjijijjilklklhukjiukhjijijkhjijkj