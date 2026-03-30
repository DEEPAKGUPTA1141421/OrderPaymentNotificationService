package com.OrderPaymentNotificationService.OrderPaymentNotificationService.constant;

import java.math.BigDecimal;

public class WalletAndLoyalityPointsConstant {
    /** RBI Prepaid Payment Instrument limit: ₹1,00,000 max balance */
    public static final BigDecimal MAX_BALANCE_PAISE = BigDecimal.valueOf(100_000L).multiply(BigDecimal.valueOf(100));
    /** Per-transaction top-up ceiling: ₹10,000 */
    public static final BigDecimal MAX_TOPUP_PAISE = BigDecimal.valueOf(10_000L).multiply(BigDecimal.valueOf(100));
    /** Daily top-up limit: ₹20,000 (RBI guideline for semi-closed wallets) */
    public static final BigDecimal DAILY_TOPUP_LIMIT_PAISE = BigDecimal.valueOf(20_000L)
            .multiply(BigDecimal.valueOf(100));
    public static final String UPI = "UPI";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String MERCHANT_ORDER_ID = "merchantOrderId";
    public static final String SUCCESS = "SUCCESS";
    public static final String PENDING = "PENDING";
    public static final String ORDER = "ORDER";
    public static final long MAX_METHODS_PER_USER = 10;
}
