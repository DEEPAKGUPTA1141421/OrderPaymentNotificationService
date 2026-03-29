package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.*;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Wallet;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction.TransactionSource;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction.TransactionStatus;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction.TransactionType;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.WalletRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.WalletTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant.WalletAndLoyalityPointsConstant.MAX_TOPUP_PAISE;
import static com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant.WalletAndLoyalityPointsConstant.DAILY_TOPUP_LIMIT_PAISE;
import static com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant.WalletAndLoyalityPointsConstant.MAX_BALANCE_PAISE;
import static com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant.WalletAndLoyalityPointsConstant.UPI;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService extends BaseService {
    private final WalletRepository walletRepo;
    private final WalletTransactionRepository txRepo;

    @Transactional(readOnly = true)
    public ApiResponse<Object> getWallet() {
        try {
            Wallet wallet = getOrCreateWallet();
            List<WalletTxnDto> recent = txRepo
                    .findTop5ByUserIdOrderByCreatedAtDesc(getUserId())
                    .stream()
                    .map(this::toTxnDto)
                    .toList();

            WalletBalanceDto dto = WalletBalanceDto.builder()
                    .walletId(wallet.getId())
                    .balanceRupees(toRupees(wallet.getBalancePaise()))
                    .balancePaise(wallet.getBalancePaise().longValue())
                    .frozen(wallet.isFrozen())
                    .frozenReason(wallet.getFrozenReason())
                    .recentTransactions(recent)
                    .build();

            return new ApiResponse<>(true, "Wallet fetched", dto, 200);
        } catch (Exception e) {
            log.error("getWallet failed for user {}: {}", getUserId(), e.getMessage());
            return new ApiResponse<>(false, "Failed to fetch wallet", null, 500);
        }
    }

    @Transactional
    public ApiResponse<Object> addMoney(AddMoneyRequestDto dto) {
        try {
            // 1. Idempotency check — if same key already succeeded, return cached result
            Optional<WalletTransaction> existing = txRepo.findByIdempotencyKey(dto.getIdempotencyKey());
            if (existing.isPresent()) {
                WalletTransaction prev = existing.get();
                if (prev.getStatus() == TransactionStatus.SUCCESS) {
                    return new ApiResponse<>(true,
                            "Already processed (idempotent)",
                            buildAddMoneyResponse(prev, "SUCCESS"),
                            200);
                }
            }

            // 2. Convert rupees → paise (avoid floating-point with BigDecimal)
            BigDecimal amountPaise = toPaises(dto.getAmountRupees());

            // 3. Per-transaction ceiling
            if (amountPaise.compareTo(MAX_TOPUP_PAISE) > 0) {
                return new ApiResponse<>(false,
                        "Single top-up cannot exceed ₹10,000", null, 400);
            }

            // 4. Acquire pessimistic lock on wallet row
            Wallet wallet = walletRepo.findByUserIdForUpdate(getUserId())
                    .orElseGet(() -> createWallet());

            // 5. Frozen wallet check
            if (wallet.isFrozen()) {
                return new ApiResponse<>(false,
                        "Wallet is frozen. Reason: " + wallet.getFrozenReason(), null, 403);
            }

            // 6. Daily top-up limit (rolling 24-hour window)
            ZonedDateTime since = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).minusHours(24);
            Long dailyTopUpPaise = txRepo.sumTopUpAmountSince(getUserId(), since);
            if (dailyTopUpPaise == null)
                dailyTopUpPaise = 0L;
            if (BigDecimal.valueOf(dailyTopUpPaise).add(amountPaise)
                    .compareTo(DAILY_TOPUP_LIMIT_PAISE) > 0) {
                return new ApiResponse<>(false,
                        "Daily top-up limit of ₹20,000 exceeded", null, 400);
            }

            // 7. Max wallet balance check (RBI rule)
            if (wallet.getBalancePaise().add(amountPaise).compareTo(MAX_BALANCE_PAISE) > 0) {
                return new ApiResponse<>(false,
                        "Wallet balance cannot exceed ₹1,00,000 (RBI regulation)", null, 400);
            }

            // 8. Validate UPI ID if payment method is UPI
            if (UPI.equals(dto.getPaymentMethod()) && dto.getUpiId() != null) {
                validateUpiId(dto.getUpiId());
            }

            // 9. ─── PAYMENT GATEWAY INTEGRATION POINT ───────────────────────
            // In production, call Razorpay / Cashfree / Payu here.
            // They return a gatewayOrderId + paymentUrl.
            // We create a PENDING transaction first, then confirm via webhook.
            //
            // For this implementation, we simulate a synchronous success
            // (replace with async gateway callback pattern in production).
            String gatewayOrderId = "MOCK_GW_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            // ────────────────────────────────────────────────────────────────

            // 10. Credit wallet
            wallet.credit(amountPaise);
            walletRepo.save(wallet);

            // 11. Record transaction
            WalletTransaction txn = WalletTransaction.builder()
                    .wallet(wallet)
                    .userId(getUserId())
                    .type(TransactionType.CREDIT)
                    .source(TransactionSource.TOP_UP)
                    .amountPaise(amountPaise)
                    .closingBalancePaise(wallet.getBalancePaise())
                    .referenceId(gatewayOrderId)
                    .description("Wallet top-up via " + dto.getPaymentMethod())
                    .idempotencyKey(dto.getIdempotencyKey())
                    .status(TransactionStatus.SUCCESS)
                    .build();
            txRepo.save(txn);

            log.info("Wallet top-up success: userId={}, amountPaise={}, txnId={}",
                    getUserId(), amountPaise, txn.getId());

            return new ApiResponse<>(true, "Money added successfully",
                    buildAddMoneyResponse(txn, "SUCCESS"), 200);

        } catch (

        IllegalStateException e) {
            log.warn("addMoney business rule violation for user {}: {}", getUserId(), e.getMessage());
            return new ApiResponse<>(false, e.getMessage(), null, 400);
        } catch (Exception e) {
            log.error("addMoney failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Payment processing failed. Please try again.", null, 500);
        }
    }

    public Wallet getOrCreateWallet() {
        return walletRepo.findByUserId(getUserId())
                .orElseGet(() -> createWallet());
    }

    private Wallet createWallet() {
        Wallet w = Wallet.builder().userId(getUserId()).build();
        return walletRepo.save(w);
    }

    private WalletTxnDto toTxnDto(WalletTransaction t) {
        return WalletTxnDto.builder()
                .id(t.getId())
                .type(t.getType().name())
                .source(t.getSource().name())
                .amountRupees(toRupees(t.getAmountPaise()))
                .closingBalanceRupees(toRupees(t.getClosingBalancePaise()))
                .referenceId(t.getReferenceId())
                .description(t.getDescription())
                .status(t.getStatus().name())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private void validateUpiId(String upiId) {
        if (upiId == null || !upiId.matches("^[a-zA-Z0-9._\\-]{2,256}@[a-zA-Z]{2,64}$")) {
            throw new IllegalArgumentException("Invalid UPI ID format: " + upiId);
        }
    }

    private AddMoneyResponseDto buildAddMoneyResponse(WalletTransaction txn, String status) {
        return AddMoneyResponseDto.builder()
                .walletTransactionId(txn.getId())
                .gatewayOrderId(txn.getReferenceId())
                .amountRupees(toRupees(txn.getAmountPaise()))
                .status(status)
                .message("₹" + toRupees(txn.getAmountPaise()) + " added to your wallet")
                .build();
    }
}
