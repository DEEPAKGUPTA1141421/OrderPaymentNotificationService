package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sponsored_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsoredSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Page page; // Which page this slot belongs to

    private int slotNumber; // Slot position (1-6 per page)

    private String price; // Fixed price for 7 days

    private boolean active; // Available for booking

    private String bookedBy; // shop/brand id
    private String bookedEntityName; // e.g., "GoBoult"

    private UUID productId; // if sponsored is for single product
    private String brandId; // if sponsored is for brand

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public enum Page {
        FORYOU,
        FASHION,
        MOBILE,
        ELECTRONICS,
        ACCESSORIES,
        BEAUTY
    }
}
