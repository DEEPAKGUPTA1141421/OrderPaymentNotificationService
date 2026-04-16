package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartResponseDto {

    private UUID   cartId;
    private UUID   userId;
    private String status;           // "ACTIVE"

    private List<CartItemDto> items;

    /**
     * Pre-calculated per-shop breakdown provided by the cart service.
     * This is the source of truth for booking amounts — no re-calculation needed.
     * One SubOrderDto per seller/shop.
     */
    private List<SubOrderDto> subOrders;

    // ── Cart-level financials (all in rupees) ─────────────────────────────────
    private double totalAmount;      // raw item total (sum of price × qty)
    private double totalDiscount;    // total discount including coupon
    private double serviceCharge;    // platform-level charge (distributed across shops)
    private double deliveryCharge;   // total delivery
    private double gstCharge;        // total GST
    private double grandTotal;       // what the user actually pays

    // ── Coupon ────────────────────────────────────────────────────────────────
    private String cartCoupon;        // e.g. "BIG1000"
    private String cartLineDiscount;  // coupon discount amount as string, e.g. "1000"

    /** Any stock/availability issues detected by the cart service. */
    private List<String> validationIssues;

    // ══════════════════════════════════════════════════════════════════════════
    //  Per-shop sub-order breakdown (cart service does all the math for us)
    // ══════════════════════════════════════════════════════════════════════════

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubOrderDto {
        private UUID             shopId;
        private List<CartItemDto> items;

        // ── Already-calculated financials for this shop (rupees) ─────────────
        private double subTotal;                  // raw item total for this shop
        private double itemLevelDiscount;         // item-specific discounts
        private double proportionalCartDiscount;  // share of the coupon discount
        private double taxableAmount;             // subTotal − discounts
        private double gstCharge;                 // GST on taxable amount
        private double deliveryCharge;            // delivery for this shop
        /**
         * subOrderTotal = taxableAmount + gstCharge + deliveryCharge
         * NOTE: excludes serviceCharge (that is cart-level and must be added separately).
         */
        private double subOrderTotal;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Individual cart item
    // ══════════════════════════════════════════════════════════════════════════

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemDto {
        private UUID   id;           // cart-item id
        private UUID   productId;
        private UUID   variantId;
        private UUID   shopId;
        private int    quantity;
        private double price;        // unit price in rupees

        // ── Display / availability ────────────────────────────────────────────
        private String  name;
        private String  image;
        private String  description;
        private String  appliedCoupon;
        private String  discountLineAmount;  // item-level discount as string
        private int     stockAvailable;
        private boolean available;
    }
}
