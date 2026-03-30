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
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy.PaymentGateway;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy.PaymentGatewayFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant.WalletAndLoyalityPointsConstant.*;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService extends BaseService {
    private final WalletRepository walletRepo;
    private final WalletTransactionRepository txRepo;
    private final PaymentGatewayFactory gatewayFactory;

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
                            buildAddMoneyResponse(prev, SUCCESS, null),
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
            WalletTransaction walletTransaction = createWalletTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOP_UP, amountPaise, dto.getPaymentMethod(), dto.getIdempotencyKey());
            txRepo.save(walletTransaction);
            Map<String, Object> phonePeResponse;
            String merchantOrderId = walletTransaction.getId().toString();

            PaymentGateway paymentGateway = gatewayFactory.getGateway(dto.getGateway());
            try {
                phonePeResponse = paymentGateway.createOrder(
                        merchantOrderId,
                        toPaises(dto.getAmountRupees()).toPlainString(),
                        dto.getIdempotencyKey());
            } catch (Exception e) {
                // Mark the pending txn as FAILED so idempotency key can be retried
                walletTransaction.setStatus(TransactionStatus.FAILED);
                txRepo.save(walletTransaction);
                log.error("PhonePe createOrder failed for user {}: {}", getUserId(), e.getMessage(), e);
                return new ApiResponse<>(false,
                        "Payment gateway error. Please try again.", null, 502);
            }
            // 11. ── STEP C: Persist the PhonePe orderId so webhook can reconcile ──────
            String phonePeOrderId = String.valueOf(phonePeResponse.get("orderId"));
            String phonePeToken = String.valueOf(phonePeResponse.get("token"));
            walletTransaction.setReferenceId(phonePeOrderId);
            txRepo.save(walletTransaction);

            log.info("PhonePe order created: userId={}, amountPaise={}, merchantOrderId={}, phonePeOrderId={}",
                    getUserId(), amountPaise, merchantOrderId, phonePeOrderId);
            // 12. Return token + orderId to frontend — frontend launches PhonePe payment
            // sheet
            AddMoneyResponseDto responseDto = AddMoneyResponseDto.builder()
                    .walletTransactionId(walletTransaction.getId())
                    .gatewayOrderId(phonePeOrderId)
                    .gatewayPaymentUrl(phonePeToken) // token is used by PhonePe JS/Android SDK
                    .amountRupees(dto.getAmountRupees())
                    .status(PENDING)
                    .message("Payment initiated. Complete payment via PhonePe.")
                    .build();

            return new ApiResponse<>(true, "Payment initiated", responseDto, 200);
        } catch (

        IllegalStateException e) {
            log.warn("addMoney business rule violation for user {}: {}", getUserId(), e.getMessage());
            return new ApiResponse<>(false, e.getMessage(), null, 400);
        } catch (Exception e) {
            log.error("addMoney failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Payment processing failed. Please try again.", null, 500);
        }
    }

    @Transactional(readOnly = true)
    public ApiResponse<Object> getTransactionHistory(int page, int size) {
        try {
            // Clamp page size to prevent abuse
            int safeSize = Math.min(Math.max(size, 1), 50);
            Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
            Page<WalletTransaction> txPage = txRepo.findByUserIdOrderByCreatedAtDesc(getUserId(), pageable);

            var response = new java.util.HashMap<String, Object>();
            response.put("transactions", txPage.getContent().stream().map(this::toTxnDto).toList());
            response.put("totalElements", txPage.getTotalElements());
            response.put("totalPages", txPage.getTotalPages());
            response.put("currentPage", txPage.getNumber());

            return new ApiResponse<>(true, "Transaction history fetched", response, 200);
        } catch (Exception e) {
            log.error("getTransactionHistory failed for user {}: {}", getUserId(), e.getMessage());
            return new ApiResponse<>(false, "Failed to fetch history", null, 500);
        }
    }

    @Transactional
    public WalletTransaction debitForOrder(BigDecimal amountPaise,
            String orderId, String idempotencyKey) {
        // Idempotency
        Optional<WalletTransaction> dup = txRepo.findByIdempotencyKey(idempotencyKey);
        if (dup.isPresent())
            return dup.get();

        Wallet wallet = walletRepo.findByUserIdForUpdate(getUserId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found"));

        if (wallet.isFrozen())
            throw new IllegalStateException("Wallet is frozen");

        wallet.debit(amountPaise); // throws if insufficient
        walletRepo.save(wallet);

        WalletTransaction txn = WalletTransaction.builder()
                .wallet(wallet)
                .userId(getUserId())
                .type(TransactionType.DEBIT)
                .source(TransactionSource.ORDER_PAYMENT)
                .amountPaise(amountPaise)
                .closingBalancePaise(wallet.getBalancePaise())
                .referenceId(orderId)
                .description("Payment for order " + orderId)
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.SUCCESS)
                .build();
        return txRepo.save(txn);
    }

    @Transactional
    public WalletTransaction creditRefund(BigDecimal amountPaise,
            String orderId, String idempotencyKey) {
        Optional<WalletTransaction> dup = txRepo.findByIdempotencyKey(idempotencyKey);
        if (dup.isPresent())
            return dup.get();

        Wallet wallet = walletRepo.findByUserIdForUpdate(getUserId())
                .orElseGet(() -> createWallet());

        // Refunds bypass the max-balance check (regulatory requirement)
        wallet.credit(amountPaise);
        walletRepo.save(wallet);

        WalletTransaction txn = WalletTransaction.builder()
                .wallet(wallet)
                .userId(getUserId())
                .type(TransactionType.CREDIT)
                .source(TransactionSource.ORDER_REFUND)
                .amountPaise(amountPaise)
                .closingBalancePaise(wallet.getBalancePaise())
                .referenceId(orderId)
                .description("Refund for order " + orderId)
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.SUCCESS)
                .build();
        return txRepo.save(txn);
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

    private AddMoneyResponseDto buildAddMoneyResponse(WalletTransaction txn, String status, String token) {
        return AddMoneyResponseDto.builder()
                .walletTransactionId(txn.getId())
                .gatewayOrderId(txn.getReferenceId())
                .gatewayPaymentUrl(token)
                .amountRupees(toRupees(txn.getAmountPaise()))
                .status(status)
                .message("₹" + toRupees(txn.getAmountPaise()) + " added to your wallet")
                .build();
    }

    private WalletTransaction createWalletTransaction(Wallet wallet, TransactionType creditType,
            TransactionSource transactionSource, BigDecimal amountPaise, String paymentMethod, String idempotetentKey) {
        WalletTransaction pendingTxn = WalletTransaction.builder()
                .wallet(wallet)
                .userId(getUserId())
                .type(creditType)
                .source(transactionSource)
                .amountPaise(amountPaise)
                .closingBalancePaise(wallet.getBalancePaise()) // snapshot before credit
                .description("Wallet top-up via " + paymentMethod)
                .idempotencyKey(idempotetentKey)
                .status(TransactionStatus.PENDING)
                // referenceId will be set to PhonePe orderId after SDK call
                .build();
        return pendingTxn;
    }
}
