package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "shop_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopStats {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID sellerId;

    private long orderCount;
    private double avgRating; // optional, can be updated by other flows
    private long recentOrders; // orders within recent window
    private String city;
    private String category;
}
