package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "sponsored_sections")
@Data
public class SponsoredSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title; // e.g. "Sponsored by GoBoult"
    private String description; // e.g. "Biggest price drop"
    private String imageUrl; // promotional banner/logo

    @Enumerated(EnumType.ORDINAL)
    private SponsorshipType type; // PRODUCT or BRAND

    private UUID refId; // productId or brandId (points to what is sponsored)

    private double fixedPrice; // e.g. ₹10,000 per 7 days
    private LocalDate startDate;
    private LocalDate endDate;

    private boolean active; // is campaign active

    public enum SponsorshipType {
        PRODUCT, // seller promotes a specific product
        BRAND // seller promotes the entire brand/shop
    }

}
// mniujiuhjiujljljl,m m