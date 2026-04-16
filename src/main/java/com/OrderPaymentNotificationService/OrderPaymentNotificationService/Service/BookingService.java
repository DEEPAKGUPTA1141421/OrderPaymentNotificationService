package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.CartResponseDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.CartResponseDto.CartItemDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.CartResponseDto.SubOrderDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.BookingItem;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingItemRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.network.ProductClient;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.DateTimeUtil;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService extends BaseService {

    private static final long BOOKING_EXPIRY_MINUTES = 5;
    private static final int STOCK_LOCK_TTL_MINUTES = 10;

    private final BookingRepository bookingRepo;
    private final BookingItemRepository bookingItemRepo;
    private final ProductClient productClient;
    private final RedisLockService redisLockService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    // ══════════════════════════════════════════════════════════════════════════
    // Public entry point
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public ApiResponse<Object> createBookingFromCart(UUID deliveryAddress) {
        try {
            // guardAgainstConcurrentCheckout(); i will uncomment this

            CartResponseDto cart = fetchAndValidateCart();

            double sumSubOrderTotals = sumSubOrderTotals(cart);
            BigDecimal serviceCharge = BigDecimal.valueOf(cart.getServiceCharge());
            Instant expiresAt = bookingExpiry();

            List<Map<String, Object>> summaries = new ArrayList<>();

            for (SubOrderDto subOrder : cart.getSubOrders()) {
                validateStockAvailability(subOrder);

                BigDecimal bookingTotal = calculateBookingTotal(subOrder, serviceCharge, sumSubOrderTotals);
                BigDecimal shopService = proportionalServiceCharge(subOrder, serviceCharge, sumSubOrderTotals);
                String totalPaise = toPaise(bookingTotal);

                Booking booking = buildBooking(subOrder, deliveryAddress, totalPaise, expiresAt);
                lockAndAttachItems(booking, subOrder.getItems());
                bookingRepo.save(booking);

                log.info("Booking created | bookingId={} shopId={} userId={} amountPaise={}",
                        booking.getId(), subOrder.getShopId(), getUserId(), totalPaise);

                summaries.add(buildSummary(booking, subOrder, bookingTotal, shopService, expiresAt));
            }

            return successResponse(summaries, cart, expiresAt);

        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 409);
        } catch (Exception e) {
            log.error("Booking creation failed | userId={} error={}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Booking creation failed: " + e.getMessage(), null, 500);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 1 — Concurrency guard
    // ══════════════════════════════════════════════════════════════════════════

    private void guardAgainstConcurrentCheckout() {
        if (!redisLockService.acquireCartLock(getUserId(), BOOKING_EXPIRY_MINUTES)) {
            throw new IllegalStateException(
                    "Checkout already in progress for this account. Please wait.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 2 — Fetch + validate cart
    // ══════════════════════════════════════════════════════════════════════════

    private CartResponseDto fetchAndValidateCart() {
        ApiResponse<CartResponseDto> response = productClient.getCartInternal(getUserId(), internalApiKey);

        if (isNetworkCallFail(response.statusCode(), response.success())) {
            throw new RuntimeException("Cart service error: " + response.message());
        }

        CartResponseDto cart = response.data();

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }
        if (cart.getValidationIssues() != null && !cart.getValidationIssues().isEmpty()) {
            throw new IllegalStateException("Cart has issues: " + cart.getValidationIssues());
        }
        if (cart.getSubOrders() == null || cart.getSubOrders().isEmpty()) {
            throw new RuntimeException("Cart subOrders breakdown is missing");
        }

        return cart;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 3 — Stock availability guard (per sub-order)
    // ══════════════════════════════════════════════════════════════════════════

    private void validateStockAvailability(SubOrderDto subOrder) {
        for (CartItemDto ci : subOrder.getItems()) {
            if (!ci.isAvailable()) {
                throw new IllegalStateException("Item '" + ci.getName() + "' is out of stock.");
            }
            if (ci.getQuantity() > ci.getStockAvailable()) {
                throw new IllegalStateException(
                        "Only " + ci.getStockAvailable() + " unit(s) available for '" + ci.getName() + "'.");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 4 — Amount calculation helpers
    // ══════════════════════════════════════════════════════════════════════════

    private double sumSubOrderTotals(CartResponseDto cart) {
        double sum = cart.getSubOrders().stream()
                .mapToDouble(SubOrderDto::getSubOrderTotal)
                .sum();
        if (sum == 0)
            throw new IllegalStateException("Calculated order total is zero.");
        return sum;
    }

    private BigDecimal proportionalServiceCharge(SubOrderDto subOrder,
            BigDecimal serviceCharge,
            double sumSubOrderTotals) {
        BigDecimal ratio = BigDecimal.valueOf(subOrder.getSubOrderTotal())
                .divide(BigDecimal.valueOf(sumSubOrderTotals), 10, RoundingMode.HALF_UP);
        return serviceCharge.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBookingTotal(SubOrderDto subOrder,
            BigDecimal serviceCharge,
            double sumSubOrderTotals) {
        return BigDecimal.valueOf(subOrder.getSubOrderTotal())
                .add(proportionalServiceCharge(subOrder, serviceCharge, sumSubOrderTotals));
    }

    private String toPaise(BigDecimal rupees) {
        return rupees.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 5 — Build Booking entity
    // ══════════════════════════════════════════════════════════════════════════

    private Booking buildBooking(SubOrderDto subOrder, UUID deliveryAddress,
            String totalPaise, Instant expiresAt) {
        Booking booking = new Booking();
        booking.setUserId(getUserId());
        booking.setShopId(subOrder.getShopId());
        booking.setDeliveryAddress(deliveryAddress);
        booking.setTotalAmount(totalPaise);
        booking.setExpiresAt(expiresAt);
        booking.setStatus(Booking.Status.INITIATED);
        booking.setItems(new ArrayList<>());
        return booking;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 6 — Lock stock + attach BookingItems
    // ══════════════════════════════════════════════════════════════════════════

    private void lockAndAttachItems(Booking booking, List<CartItemDto> items) {
        for (CartItemDto ci : items) {
            // acquireStockLock(ci); i will uncomment this
            booking.getItems().add(toBookingItem(booking, ci));
        }
    }

    private void acquireStockLock(CartItemDto ci) {
        boolean locked = redisLockService.acquireLock(
                RedisLockService.LOCK_PREFIX,
                getUserId(),
                ci.getProductId(),
                ci.getQuantity(),
                STOCK_LOCK_TTL_MINUTES);
        if (!locked) {
            log.warn("Stock lock failed | userId={} productId={} item='{}'",
                    getUserId(), ci.getProductId(), ci.getName());
            throw new IllegalStateException(
                    "'" + ci.getName() + "' is being checked out in another session. Try again.");
        }
    }

    private BookingItem toBookingItem(Booking booking, CartItemDto ci) {
        BookingItem bi = new BookingItem();
        bi.setBooking(booking);
        bi.setProductId(ci.getProductId());
        bi.setVariantId(ci.getVariantId());
        bi.setQuantity(ci.getQuantity());
        bi.setPrice(toPaise(BigDecimal.valueOf(ci.getPrice())));
        return bi;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 7 — Build response payload
    // ══════════════════════════════════════════════════════════════════════════

    private Map<String, Object> buildSummary(Booking booking, SubOrderDto subOrder,
            BigDecimal bookingTotal, BigDecimal shopService,
            Instant expiresAt) {
        return Map.of(
                "bookingId", booking.getId(),
                "shopId", subOrder.getShopId(),
                "itemCount", subOrder.getItems().size(),
                "totalAmountPaise", booking.getTotalAmount(),
                "totalAmountRupees", bookingTotal.setScale(2, RoundingMode.HALF_UP),
                "breakdownRupees", buildBreakdown(subOrder, shopService, bookingTotal),
                "expiresAt", expiresAt.toString(),
                "status", "INITIATED");
    }

    private Map<String, Object> buildBreakdown(SubOrderDto s, BigDecimal shopService, BigDecimal grandTotal) {
        return Map.of(
                "subTotal", scale(s.getSubTotal()),
                "itemDiscount", scale(s.getItemLevelDiscount()).negate(),
                "couponDiscount", scale(s.getProportionalCartDiscount()).negate(),
                "gst", scale(s.getGstCharge()),
                "delivery", scale(s.getDeliveryCharge()),
                "serviceCharge", shopService,
                "grandTotal", grandTotal.setScale(2, RoundingMode.HALF_UP));
    }

    private ApiResponse<Object> successResponse(List<Map<String, Object>> summaries,
            CartResponseDto cart, Instant expiresAt) {
        return new ApiResponse<>(true,
                summaries.size() + " booking(s) created — pay for each package when it arrives.",
                Map.of(
                        "bookings", summaries,
                        "totalBookings", summaries.size(),
                        "couponApplied", Optional.ofNullable(cart.getCartCoupon()).orElse(""),
                        "expiresAt", expiresAt.toString()),
                201);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tiny utilities
    // ══════════════════════════════════════════════════════════════════════════

    private Instant bookingExpiry() {
        return DateTimeUtil.plusMinutesInstant(BOOKING_EXPIRY_MINUTES);
    }

    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isNetworkCallFail(int statusCode, boolean success) {
        return !(statusCode >= 200 && statusCode < 300) || !success;
    }
}
// jjhjjj hoj jj ji jook jok ojhuku ygu uiuhuuhu