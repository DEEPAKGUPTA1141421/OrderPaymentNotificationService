package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.Strategy;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayFactory {

    private final Map<String, PaymentGateway> gateways;

    @Autowired
    public PaymentGatewayFactory(Map<String, PaymentGateway> gateways) {
        this.gateways = gateways;
    }

    public PaymentGateway getGateway(String type) {
        String key = switch (type.toLowerCase()) {
            case "phonepe" -> "phonepeGateway";
            // Add more cases later
            default -> throw new IllegalArgumentException("Unsupported payment gateway: " + type);
        };
        return gateways.get(key);
    }
}
