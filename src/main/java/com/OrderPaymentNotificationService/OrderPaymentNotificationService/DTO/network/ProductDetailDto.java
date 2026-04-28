package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailDto {

    private UUID   productId;
    private UUID   variantId;
    private UUID   shopId;

    private String name;
    private String image;
    private String description;

    private double price;           // unit selling price in rupees (after any item-level discount)
    private double gstRate;         // GST percentage (e.g. 18.0 → 18%)
    private double deliveryCharge;  // fixed delivery charge in rupees for this item

    private int     stockAvailable;
    private boolean available;
}
