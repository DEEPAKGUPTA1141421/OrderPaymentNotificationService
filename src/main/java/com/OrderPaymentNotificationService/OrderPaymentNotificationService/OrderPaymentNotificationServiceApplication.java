package com.OrderPaymentNotificationService.OrderPaymentNotificationService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableFeignClients
@EnableAspectJAutoProxy
public class OrderPaymentNotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderPaymentNotificationServiceApplication.class, args);
	}

}
// jojuiuj joiio uui huihuinkkjhnj jk hjjjjjj
// hkujuioufhkuui uiuifriuuh iuuiufir huiuir