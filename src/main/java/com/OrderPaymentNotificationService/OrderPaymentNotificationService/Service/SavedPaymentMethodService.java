package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.SaveCardRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.SaveUpiRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.SavedMethodDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SavedPaymentMethod;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.SavedPaymentMethod.MethodType;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.SavedPaymentMethodRepository;

import static com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant.WalletAndLoyalityPointsConstant.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavedPaymentMethodService extends BaseService {
    private final SavedPaymentMethodRepository savedPaymentMethodRepository;

    @Transactional(readOnly = true)
    public ApiResponse<Object> getAll(UUID userId) {
        try {
            List<SavedMethodDto> methods = savedPaymentMethodRepository
                    .findByUserIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(userId)
                    .stream()
                    .map(this::toDto)
                    .toList();

            return new ApiResponse<>(true, "Payment methods fetched", methods, 200);
        } catch (Exception e) {
            log.error("getAll payment methods failed for user {}: {}", userId, e.getMessage());
            return new ApiResponse<>(false, "Failed to fetch payment methods", null, 500);
        }
    }

    @Transactional
    public ApiResponse<Object> saveCard(SaveCardRequest req) {
        try {
            // 1. Cap total saved methods
            if (savedPaymentMethodRepository.countByUserIdAndActiveTrue(getUserId()) >= MAX_METHODS_PER_USER) {
                return new ApiResponse<>(false,
                        "Maximum " + MAX_METHODS_PER_USER + " payment methods allowed. Remove one first.",
                        null, 400);
            }

            // 2. Validate expiry: must be future month
            validateCardExpiry(req.getCardExpiry());

            // 3. Validate Luhn-equivalent safety: last-4 must be digits
            if (!req.getCardLast4().matches("\\d{4}")) {
                return new ApiResponse<>(false, "cardLast4 must be 4 digits", null, 400);
            }

            // 4. Duplicate gateway token check (same card already saved)
            if (savedPaymentMethodRepository.findByUserIdAndGatewayToken(getUserId(), req.getGatewayToken())
                    .isPresent()) {
                return new ApiResponse<>(false,
                        "This card is already saved to your account", null, 409);
            }

            // 5. If makeDefault, clear existing default
            if (req.isMakeDefault()) {
                savedPaymentMethodRepository.clearDefaultForUser(getUserId());
            }

            SavedPaymentMethod method = createSavedPaymentMethod(req);
            SavedPaymentMethod saved = savedPaymentMethodRepository.save(method);
            log.info("Card saved for user {}: last4={}, brand={}", getUserId(),
                    req.getCardLast4(), req.getCardBrand());

            return new ApiResponse<>(true, "Card saved successfully", toDto(saved), 201);

        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 400);
        } catch (Exception e) {
            log.error("saveCard failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to save card", null, 500);
        }
    }

    @Transactional
    public ApiResponse<Object> saveUpi(SaveUpiRequest req) {
        try {
            if (savedPaymentMethodRepository.countByUserIdAndActiveTrue(getUserId()) >= MAX_METHODS_PER_USER) {
                return new ApiResponse<>(false,
                        "Maximum " + MAX_METHODS_PER_USER + " payment methods allowed.", null, 400);
            }

            // Normalise UPI ID to lowercase
            String normalizedUpiId = req.getUpiId().trim().toLowerCase();

            // Duplicate check
            if (savedPaymentMethodRepository.findByUserIdAndUpiId(getUserId(), normalizedUpiId).isPresent()) {
                return new ApiResponse<>(false,
                        "This UPI ID is already saved", null, 409);
            }

            // Validate UPI ID format (belt-and-suspenders on top of @Pattern)
            validateUpiId(normalizedUpiId);

            if (req.isMakeDefault()) {
                savedPaymentMethodRepository.clearDefaultForUser(getUserId());
            }

            SavedPaymentMethod method = SavedPaymentMethod.builder()
                    .userId(getUserId())
                    .methodType(MethodType.UPI)
                    .upiId(normalizedUpiId)
                    .upiDisplayName(req.getUpiDisplayName())
                    .nickname(req.getNickname())
                    .isDefault(req.isMakeDefault())
                    .active(true)
                    .build();

            SavedPaymentMethod saved = savedPaymentMethodRepository.save(method);
            log.info("UPI saved for user {}: upiId={}", getUserId(), normalizedUpiId);

            return new ApiResponse<>(true, "UPI ID saved successfully", toDto(saved), 201);

        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 400);
        } catch (Exception e) {
            log.error("saveUpi failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to save UPI ID", null, 500);
        }
    }

    @Transactional
    public ApiResponse<Object> delete(UUID methodId) {
        try {
            SavedPaymentMethod method = savedPaymentMethodRepository.findByIdAndUserId(methodId, getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Payment method not found"));

            if (!method.isActive()) {
                return new ApiResponse<>(false, "Payment method already removed", null, 400);
            }

            // Soft delete — preserve for audit/reconciliation
            method.setActive(false);

            // If this was default, clear the default (user must pick a new one)
            if (method.isDefault()) {
                method.setDefault(false);
            }

            savedPaymentMethodRepository.save(method);

            // Also revoke the token from the payment gateway (fire-and-forget)
            revokeGatewayTokenAsync(method);

            log.info("Payment method soft-deleted: id={}, user={}", methodId, getUserId());
            return new ApiResponse<>(true, "Payment method removed", null, 200);

        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 404);
        } catch (Exception e) {
            log.error("delete payment method failed: {}", e.getMessage(), e);
            return new ApiResponse<>(false, "Failed to remove payment method", null, 500);
        }
    }

    @Transactional(readOnly = true)
    public ApiResponse<Object> getAll() {
        try {
            List<SavedMethodDto> methods = savedPaymentMethodRepository
                    .findByUserIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(getUserId())
                    .stream()
                    .map(this::toDto)
                    .toList();

            return new ApiResponse<>(true, "Payment methods fetched", methods, 200);
        } catch (Exception e) {
            log.error("getAll payment methods failed for user {}: {}", getUserId(), e.getMessage());
            return new ApiResponse<>(false, "Failed to fetch payment methods", null, 500);
        }
    }

    public SavedPaymentMethod createSavedPaymentMethod(SaveCardRequest req) {
        SavedPaymentMethod method = SavedPaymentMethod.builder()
                .userId(getUserId())
                .methodType(MethodType.CARD)
                .gatewayToken(req.getGatewayToken()) // vault token, never raw PAN
                .cardLast4(req.getCardLast4())
                .cardBrand(req.getCardBrand().toUpperCase())
                .cardHolderName(sanitizeName(req.getCardHolderName()))
                .cardExpiry(req.getCardExpiry())
                .cardType(req.getCardType())
                .nickname(req.getNickname())
                .isDefault(req.isMakeDefault())
                .gateway(req.getGateway())
                .active(true)
                .build();
        return method;
    }

    private void validateCardExpiry(String expiry) {
        // Format MM/YYYY
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yyyy");
            YearMonth expiryYM = YearMonth.parse(expiry, fmt);
            if (expiryYM.isBefore(YearMonth.now())) {
                throw new IllegalArgumentException("Card is expired");
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid expiry format. Use MM/YYYY");
        }
    }

    private void validateUpiId(String upiId) {
        if (!upiId.matches("^[a-zA-Z0-9._\\-]{2,256}@[a-zA-Z]{2,64}$")) {
            throw new IllegalArgumentException("Invalid UPI ID format");
        }
        // Block obviously invalid handles
        String handle = upiId.split("@")[1];
        if (handle.equalsIgnoreCase("example") || handle.equalsIgnoreCase("test")) {
            throw new IllegalArgumentException("Invalid UPI ID");
        }
    }

    private String sanitizeName(String name) {
        if (name == null)
            return null;
        // Remove control characters, keep letters, spaces, hyphens, apostrophes
        return name.trim().replaceAll("[^a-zA-Z .'-]", "");
    }

    private void revokeGatewayTokenAsync(SavedPaymentMethod method) {
        // Integration point: call Razorpay/Stripe to delete their stored token
        // Use @Async in production to avoid blocking
        if (method.getGatewayToken() != null) {
            log.info("TODO: revoke gateway token {} on gateway {}",
                    method.getGatewayToken(), method.getGateway());
        }
    }

    private SavedMethodDto toDto(SavedPaymentMethod m) {
        return SavedMethodDto.builder()
                .id(m.getId())
                .methodType(m.getMethodType().name())
                .cardLast4(m.getCardLast4())
                .cardBrand(m.getCardBrand())
                .cardHolderName(m.getCardHolderName())
                .cardExpiry(m.getCardExpiry())
                .cardType(m.getCardType() != null ? m.getCardType().name() : null)
                .upiId(m.getUpiId())
                .upiDisplayName(m.getUpiDisplayName())
                .nickname(m.getNickname())
                .isDefault(m.isDefault())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
// juioie ijoljeuhjrirfzjbjfrjjkjkhjkjjjl jikiji ijjjjlkljkjkjil