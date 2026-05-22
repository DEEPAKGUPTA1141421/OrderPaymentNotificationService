package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.BuyNow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BuyNowResponseDto {

    private UUID   bookingId;
    private UUID   shopId;
    private String productName;
    private int    quantity;
    private String status;
    private String expiresAt;

    /** Raw paise string — pass directly to POST /api/v1/payment as pgPaymentAmount. */
    private String     totalAmountPaise;
    private BigDecimal totalAmountRupees;

    private Breakdown breakdown;

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Breakdown {
        private BigDecimal unitPrice;
        private int        quantity;
        private BigDecimal subTotal;
        private BigDecimal gst;
        private BigDecimal delivery;
        private BigDecimal serviceCharge;
        private BigDecimal grandTotal;
    }
}
