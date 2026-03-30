package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.LoyaltyBalanceDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.LoyaltyTxnDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.RedeemPointsRequest;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.WalletLoyaltyDto.RedeemPointsResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.LoyaltyAccount;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.LoyaltyTransaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.WalletTransaction;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.LoyaltyTransaction.TxSource;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.LoyaltyTransaction.TxType;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.LoyaltyAccountRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.LoyaltyTransactionRepository;

import static com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant.WalletAndLoyalityPointsConstant.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService extends BaseService {

    private final LoyaltyAccountRepository accountRepo;
    private final LoyaltyTransactionRepository txRepo;
    private final WalletService walletService;

    // ── Business Rule Constants ───────────────────────────────────────────────

    /** 1 loyalty point = ₹0.25 */
    private static final BigDecimal RUPEES_PER_POINT = new BigDecimal("0.25");

    /** Earn rate: ₹100 spent → 10 points */
    private static final BigDecimal POINTS_PER_HUNDRED_RUPEES = new BigDecimal("10");

    /** Minimum points per redemption */
    private static final long MIN_REDEEM_POINTS = 100L;

    /** Per-request redemption cap */
    private static final long MAX_REDEEM_PER_REQUEST = 10_000L;

    /** Daily redemption cap (fraud guard) */
    private static final long DAILY_REDEEM_CAP = 20_000L;

    /** Points expiry: 1 year from earn date */
    private static final long POINTS_EXPIRY_DAYS = 365L;

    // ── Tier thresholds ───────────────────────────────────────────────────────
    private static final long GOLD_THRESHOLD = 5_000L;
    private static final long PLATINUM_THRESHOLD = 20_000L;

    // ─────────────────────────────────────────────────────────────────────────
    // GET LOYALTY BALANCE + RECENT HISTORY
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Object> getBalance() {
        try {
            LoyaltyAccount account = getOrCreateAccount(getUserId());

            List<LoyaltyTxnDto> recent = txRepo
                    .findTop5ByUserIdOrderByCreatedAtDesc(getUserId())
                    .stream()
                    .map(this::toTxnDto)
                    .toList();

            LoyaltyBalanceDto dto = LoyaltyBalanceDto.builder()
                    .loyaltyAccountId(account.getId())
                    .pointsBalance(account.getPointsBalance())
                    .lifetimeEarned(account.getLifetimeEarned())
                    .lifetimeRedeemed(account.getLifetimeRedeemed())
                    .tier(account.getTier().name())
                    .tierProgress(buildTierProgress(account))
                    .pointsToNextTier(pointsToNextTier(account))
                    .pointsValueRupees(toRupees(account.getPointsBalance()))
                    .recentTransactions(recent)
                    .build();

            return new ApiResponse<>(true, "Loyalty balance fetched", dto, 200);
        } catch (Exception e) {
            log.error("getBalance failed for user {}: {}", getUserId(), e.getMessage());
            return new ApiResponse<>(false, "Failed to fetch loyalty balance", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FULL LOYALTY HISTORY (paginated)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Object> getHistory(int page, int size) {
        try {
            int safeSize = Math.min(Math.max(size, 1), 50);
            Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
            Page<LoyaltyTransaction> txPage = txRepo.findByUserIdOrderByCreatedAtDesc(getUserId(), pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("transactions", txPage.getContent().stream().map(this::toTxnDto).toList());
            response.put("totalElements", txPage.getTotalElements());
            response.put("totalPages", txPage.getTotalPages());
            response.put("currentPage", txPage.getNumber());

            return new ApiResponse<>(true, "Loyalty history fetched", response, 200);
        } catch (Exception e) {
            log.error("getHistory failed for user {}: {}", getUserId(), e.getMessage());
            return new ApiResponse<>(false, "Failed to fetch loyalty history", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REDEEM LOYALTY POINTS
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Object> redeemPoints(RedeemPointsRequest req) {
        try {
            long pts = req.getPoints();

            // 1. Validation
            if (pts < MIN_REDEEM_POINTS) {
                return new ApiResponse<>(false,
                        "Minimum redemption is " + MIN_REDEEM_POINTS + " points", null, 400);
            }
            if (pts > MAX_REDEEM_PER_REQUEST) {
                return new ApiResponse<>(false,
                        "Maximum " + MAX_REDEEM_PER_REQUEST + " points per request", null, 400);
            }

            // 2. Daily cap check
            ZonedDateTime since = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).withHour(0).withMinute(0).withSecond(0);
            Long dailyRedeemed = txRepo.sumRedeemedSince(getUserId(), since);
            if (dailyRedeemed == null)
                dailyRedeemed = 0L;
            if (dailyRedeemed + pts > DAILY_REDEEM_CAP) {
                return new ApiResponse<>(false,
                        "Daily redemption limit of " + DAILY_REDEEM_CAP + " points exceeded", null, 400);
            }

            // 3. Lock and check balance
            LoyaltyAccount account = accountRepo.findByUserIdForUpdate(getUserId())
                    .orElseThrow(() -> new IllegalStateException("Loyalty account not found"));

            if (account.getPointsBalance() < pts) {
                return new ApiResponse<>(false,
                        "Insufficient points. Balance: " + account.getPointsBalance(), null, 400);
            }

            // 4. Destination: ORDER redemption (apply on order)
            if (ORDER.equals(req.getDestination())) {
                if (req.getOrderId() == null) {
                    return new ApiResponse<>(false,
                            "orderId is required when destination=ORDER", null, 400);
                }
                // Delegate to OrderService — here we just debit the points
                // and return the discount value for the order service to apply.
                account.redeemPoints(pts);
                accountRepo.save(account);

                LoyaltyTransaction txn = recordRedemption(account, getUserId(), pts,
                        req.getOrderId().toString(), req.getIdempotencyKey(),
                        "Points redeemed against order " + req.getOrderId());

                BigDecimal valueInRupees = toRupees(pts);
                log.info("Loyalty ORDER redemption: user={}, pts={}, order={}", getUserId(), pts, req.getOrderId());

                return new ApiResponse<>(true, "Points redeemed",
                        RedeemPointsResponse.builder()
                                .pointsRedeemed(pts)
                                .valueInRupees(valueInRupees)
                                .destination("ORDER")
                                .status("SUCCESS")
                                .message(pts + " points (₹" + valueInRupees + ") applied to order")
                                .build(),
                        200);
            }

            // 5. Destination: WALLET — convert points to wallet balance
            account.redeemPoints(pts);
            accountRepo.save(account);

            BigDecimal valueInRupees = toRupees(pts);
            BigDecimal valueInPaise = valueInRupees.multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP);

            // Credit the wallet
            String walletIdempotencyKey = "LOYALTY_REDEEM_" + req.getIdempotencyKey();
            WalletTransaction walletTxn = walletService.creditRefund(
                    valueInPaise,
                    "LOYALTY_" + getUserId(),
                    walletIdempotencyKey);

            LoyaltyTransaction txn = recordRedemption(account, getUserId(), pts,
                    walletTxn.getId().toString(), req.getIdempotencyKey(),
                    pts + " points redeemed into wallet");

            log.info("Loyalty WALLET redemption: user={}, pts={}, walletTxn={}",
                    getUserId(), pts, walletTxn.getId());

            return new ApiResponse<>(true, "Points redeemed into wallet",
                    RedeemPointsResponse.builder()
                            .pointsRedeemed(pts)
                            .valueInRupees(valueInRupees)
                            .destination("WALLET")
                            .status("SUCCESS")
                            .message(pts + " points (₹" + valueInRupees + ") added to your wallet")
                            .walletTransactionId(walletTxn.getId())
                            .build(),
                    200);

        } catch (

        IllegalStateException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 400);
        } catch (Exception e) {
            log.error("redeemPoints failed for user {}: {}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Redemption failed. Please try again.", null, 500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL: earn points (called by OrderService after order delivery)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public LoyaltyTransaction earnPointsForOrder(BigDecimal orderAmountRupees,
            String orderId) {
        // Earn = (orderAmount / 100) * POINTS_PER_HUNDRED_RUPEES
        long pts = orderAmountRupees
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .multiply(POINTS_PER_HUNDRED_RUPEES)
                .longValue();

        if (pts <= 0)
            return null; // order too small to earn points

        LoyaltyAccount account = accountRepo.findByUserIdForUpdate(getUserId())
                .orElseGet(() -> createAccount(getUserId()));
        account.earnPoints(pts);
        accountRepo.save(account);

        LoyaltyTransaction txn = LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .userId(getUserId())
                .type(TxType.EARN)
                .source(TxSource.ORDER_PURCHASE)
                .points(pts)
                .closingBalance(account.getPointsBalance())
                .referenceId(orderId)
                .description("Earned " + pts + " points on order " + orderId)
                .expiresAt(ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(POINTS_EXPIRY_DAYS))
                .build();
        return txRepo.save(txn);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL: welcome bonus (called on first order completion)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void grantWelcomeBonus() {
        // Idempotent: only grant once
        if (txRepo.existsByUserIdAndSource(getUserId(), TxSource.WELCOME_BONUS))
            return;

        LoyaltyAccount account = accountRepo.findByUserIdForUpdate(getUserId())
                .orElseGet(() -> createAccount(getUserId()));
        long bonus = 200L;
        account.earnPoints(bonus);
        accountRepo.save(account);

        LoyaltyTransaction txn = LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .userId(getUserId())
                .type(TxType.EARN)
                .source(TxSource.WELCOME_BONUS)
                .points(bonus)
                .closingBalance(account.getPointsBalance())
                .description("Welcome bonus: " + bonus + " points")
                .expiresAt(ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(POINTS_EXPIRY_DAYS))
                .build();
        txRepo.save(txn);
        log.info("Welcome bonus {} pts granted to user {}", bonus, getUserId());
    }

    public LoyaltyAccount getOrCreateAccount(UUID userId) {
        return accountRepo.findByUserId(userId)
                .orElseGet(() -> createAccount(userId));
    }

    private LoyaltyAccount createAccount(UUID userId) {
        return accountRepo.save(
                LoyaltyAccount.builder().userId(userId).build());
    }

    private LoyaltyTransaction recordRedemption(LoyaltyAccount account, UUID userId,
            long pts, String refId,
            String idempotencyKey, String desc) {
        return txRepo.save(LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .userId(userId)
                .type(TxType.REDEEM)
                .source(TxSource.REDEMPTION)
                .points(pts)
                .closingBalance(account.getPointsBalance())
                .referenceId(refId)
                .description(desc)
                .build());
    }

    private BigDecimal toRupees(long points) {
        return RUPEES_PER_POINT.multiply(BigDecimal.valueOf(points))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String buildTierProgress(LoyaltyAccount account) {
        long earned = account.getLifetimeEarned();
        return switch (account.getTier()) {
            case SILVER -> earned + "/" + GOLD_THRESHOLD + " to GOLD";
            case GOLD -> earned + "/" + PLATINUM_THRESHOLD + " to PLATINUM";
            case PLATINUM -> "Max tier reached";
        };
    }

    private long pointsToNextTier(LoyaltyAccount account) {
        long earned = account.getLifetimeEarned();
        return switch (account.getTier()) {
            case SILVER -> Math.max(0, GOLD_THRESHOLD - earned);
            case GOLD -> Math.max(0, PLATINUM_THRESHOLD - earned);
            case PLATINUM -> 0L;
        };
    }

    private LoyaltyTxnDto toTxnDto(LoyaltyTransaction t) {
        return LoyaltyTxnDto.builder()
                .id(t.getId())
                .type(t.getType().name())
                .source(t.getSource().name())
                .points(t.getPoints())
                .closingBalance(t.getClosingBalance())
                .referenceId(t.getReferenceId())
                .description(t.getDescription())
                .expiresAt(t.getExpiresAt())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
// huiu8 uiuouiu8 uiyu8yu7i7yu huiuuiui hijiiii