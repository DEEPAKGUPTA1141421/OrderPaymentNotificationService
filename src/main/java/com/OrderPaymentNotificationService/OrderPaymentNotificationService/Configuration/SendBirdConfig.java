package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class SendBirdConfig {

    @Value("${sendbird.master-api-token}")
    private String masterApiToken;

    @Bean("sendBirdRestTemplate")
    public RestTemplate sendBirdRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(
            (request, body, execution) -> {
                request.getHeaders().set("Api-Token", masterApiToken);
                return execution.execute(request, body);
            }
        ));
        return restTemplate;
    }
}
