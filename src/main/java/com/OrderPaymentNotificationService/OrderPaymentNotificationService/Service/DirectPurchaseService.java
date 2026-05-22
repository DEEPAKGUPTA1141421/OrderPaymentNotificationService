package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.BuyNow.BuyNowRequestDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.BuyNow.BuyNowResponseDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.ProductDetailDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.BookingItem;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.DateTimeUtil;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.network.ProductClient;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectPurchaseService extends BaseService {

    private static final long BOOKING_EXPIRY_MINUTES = 5;

    private final BookingRepository bookingRepo;
    private final ProductClient     productClient;
    private final RedisLockService  redisLockService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    // Configurable via application.yml — defaults to 2%
    @Value("${buynow.service.charge.percent:2.0}")
    private double serviceChargePercent;

    // ══════════════════════════════════════════════════════════════════════════
    // Public entry point
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public ApiResponse<Object> buyNow(BuyNowRequestDto request) {
        try {
            // 1. Prevent the same user from double-submitting the same product concurrently.
            //    Uses the same LOCK_PREFIX as cart checkout so both flows contend on the same key.
            guardAgainstConcurrentBuyNow(request.productId());

            // 2. Fetch product + variant details from Product Service via internal API.
            ProductDetailDto product = fetchProductDetails(request.productId(), request.variantId());

            // 3. Validate availability and requested quantity.
            validateProduct(product, request.quantity());

            // 4. Calculate amounts (no coupon; buy-now is always at selling price).
            BigDecimal unitPrice     = BigDecimal.valueOf(product.getPrice());
            BigDecimal subTotal      = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
            BigDecimal gst           = subTotal
                                         .multiply(BigDecimal.valueOf(product.getGstRate() / 100.0))
                                         .setScale(2, RoundingMode.HALF_UP);
            BigDecimal delivery      = BigDecimal.valueOf(product.getDeliveryCharge())
                                                 .setScale(2, RoundingMode.HALF_UP);
            BigDecimal serviceCharge = subTotal
                                         .multiply(BigDecimal.valueOf(serviceChargePercent / 100.0))
                                         .setScale(2, RoundingMode.HALF_UP);
            BigDecimal grandTotal    = subTotal.add(gst).add(delivery).add(serviceCharge);
            String     totalPaise    = toPaise(grandTotal);

            // 5. Persist Booking + BookingItem.
            Instant expiresAt = DateTimeUtil.plusMinutesInstant(BOOKING_EXPIRY_MINUTES);
            Booking booking   = buildBooking(request, product.getShopId(), totalPaise, expiresAt);

            BookingItem item = new BookingItem();
            item.setBooking(booking);
            item.setProductId(request.productId());
            item.setVariantId(request.variantId());
            item.setQuantity(request.quantity());
            item.setPrice(toPaise(unitPrice));
            booking.getItems().add(item);

            bookingRepo.save(booking);

            log.info("BuyNow booking created | bookingId={} userId={} productId={} amountPaise={}",
                    booking.getId(), getUserId(), request.productId(), totalPaise);

            return new ApiResponse<>(true, "Booking created. Proceed to payment.", buildResponse(
                    booking, product, request.quantity(), unitPrice,
                    subTotal, gst, delivery, serviceCharge, grandTotal, totalPaise, expiresAt), 201);

        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, e.getMessage(), null, 409);
        } catch (Exception e) {
            log.error("BuyNow failed | userId={} error={}", getUserId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Buy Now failed: " + e.getMessage(), null, 500);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Guards
    // ══════════════════════════════════════════════════════════════════════════

    private void guardAgainstConcurrentBuyNow(UUID productId) {
        boolean locked = redisLockService.acquireLock(
                RedisLockService.LOCK_PREFIX, getUserId(), productId, 1, BOOKING_EXPIRY_MINUTES);
        if (!locked) {
            throw new IllegalStateException(
                    "A purchase is already in progress for this product. Please wait.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Product fetch + validation
    // ══════════════════════════════════════════════════════════════════════════

    private ProductDetailDto fetchProductDetails(UUID productId, UUID variantId) {
        ApiResponse<ProductDetailDto> response =
                productClient.getProductDetailInternal(productId, variantId, internalApiKey);

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Product not found or product service unavailable.");
        }
        return response.data();
    }

    private void validateProduct(ProductDetailDto product, int quantity) {
        if (!product.isAvailable()) {
            throw new IllegalStateException(
                    "'" + product.getName() + "' is currently unavailable.");
        }
        if (quantity > product.getStockAvailable()) {
            throw new IllegalStateException(
                    "Only " + product.getStockAvailable()
                    + " unit(s) available for '" + product.getName() + "'.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Builders
    // ══════════════════════════════════════════════════════════════════════════

    private Booking buildBooking(BuyNowRequestDto req, UUID shopId,
                                 String totalPaise, Instant expiresAt) {
        Booking b = new Booking();
        b.setUserId(getUserId());
        b.setShopId(shopId);
        b.setDeliveryAddress(req.deliveryAddressId());
        b.setTotalAmount(totalPaise);
        b.setExpiresAt(expiresAt);
        b.setStatus(Booking.Status.INITIATED);
        b.setItems(new ArrayList<>());
        return b;
    }

    private BuyNowResponseDto buildResponse(Booking booking, ProductDetailDto product,
                                            int qty, BigDecimal unitPrice,
                                            BigDecimal subTotal, BigDecimal gst,
                                            BigDecimal delivery, BigDecimal serviceCharge,
                                            BigDecimal grandTotal, String totalPaise,
                                            Instant expiresAt) {
        return BuyNowResponseDto.builder()
                .bookingId(booking.getId())
                .shopId(product.getShopId())
                .productName(product.getName())
                .quantity(qty)
                .status("INITIATED")
                .expiresAt(expiresAt.toString())
                .totalAmountPaise(totalPaise)
                .totalAmountRupees(grandTotal.setScale(2, RoundingMode.HALF_UP))
                .breakdown(BuyNowResponseDto.Breakdown.builder()
                        .unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
                        .quantity(qty)
                        .subTotal(subTotal.setScale(2, RoundingMode.HALF_UP))
                        .gst(gst)
                        .delivery(delivery)
                        .serviceCharge(serviceCharge)
                        .grandTotal(grandTotal.setScale(2, RoundingMode.HALF_UP))
                        .build())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Utilities
    // ══════════════════════════════════════════════════════════════════════════

    private String toPaise(BigDecimal rupees) {
        return rupees.multiply(BigDecimal.valueOf(100))
                     .setScale(0, RoundingMode.HALF_UP)
                     .toPlainString();
    }
}
