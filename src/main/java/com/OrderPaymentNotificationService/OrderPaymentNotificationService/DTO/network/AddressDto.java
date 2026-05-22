package com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.network;

import java.util.UUID;

public record AddressDto(
        UUID   id,
        String line1,
        String city,
        String state,
        double lat,
        double lng
) {}
