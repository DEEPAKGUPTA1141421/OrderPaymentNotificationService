package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ApiResponse;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.CartResponseDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network.CartResponseDto.CartItemDto;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.Booking;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.BookingItem;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingItemRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.BookingRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.network.ProductClient;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService extends BaseService {
    private final BookingRepository bookingRepo;
    private final BookingItemRepository bookingItemRepo;
    private final ProductClient productClient;
    private final RedisLockService redisLockService;

    @Transactional
    public ApiResponse<Object> createBookingFromCart() {
        try {
            // Call Product Service via Feign
            UUID userId = getId();
            ApiResponse<CartResponseDto> response = productClient.getCart(userId);

            // Check network / 2xx status
            if (isNetworkCallFail(response.statusCode(), response.success())) {
                return new ApiResponse<>(false, response.message(), null, response.statusCode());
            }

            CartResponseDto cartDto = response.data();
            if (cartDto == null || cartDto.getItems().isEmpty()) {
                return new ApiResponse<>(false, "Cart is empty", null, 400);
            }

            // Group items by shopId
            Map<UUID, List<CartItemDto>> grouped = cartDto.getItems().stream()
                    .collect(Collectors.groupingBy(CartItemDto::getShopId));

            List<Booking> bookings = new ArrayList<>();

            for (UUID shopId : grouped.keySet()) {
                List<CartItemDto> items = grouped.get(shopId);

                BigDecimal total = BigDecimal.ZERO;
                Booking booking = new Booking();
                booking.setUserId(userId);
                booking.setShopId(shopId);
                booking.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
                booking.setItems(new ArrayList<>());

                for (CartItemDto ci : items) {
                    boolean takeLock = redisLockService.acquireLock(userId, ci.getProductId(), ci.getQuantity(), 10);
                    if (!takeLock)
                        new RuntimeException("Cannot acquire lock for product " + ci.getProductId());
                    BookingItem bi = new BookingItem();
                    bi.setBooking(booking);
                    bi.setProductId(ci.getProductId());
                    bi.setVariantId(ci.getVariantId()); // assuming no variant info in CartItemDto
                    bi.setQuantity(ci.getQuantity());
                    bi.setPrice(String.valueOf(ci.getPrice())); // store price as string

                    booking.getItems().add(bi);

                    // accumulate total
                    total = total.add(BigDecimal.valueOf(ci.getPrice())
                            .multiply(BigDecimal.valueOf(ci.getQuantity())));
                }

                booking.setTotalAmount(total.toPlainString());
                bookingRepo.save(booking);
                bookings.add(booking);
            }

            return new ApiResponse<>(true, "Bookings created", bookings, 201);
        } catch (Exception e) {
            return new ApiResponse<>(false, e.getMessage(), null, 500);
        }
    }

    private boolean isNetworkCallFail(int statusCode, boolean success) {
        if (!(statusCode >= 200 && statusCode < 300) || !success) {
            return true;
        }
        return false;
    }
}

/// nhjojj hkjhhh hhh huhu huhj ujibhuhu hhhjj