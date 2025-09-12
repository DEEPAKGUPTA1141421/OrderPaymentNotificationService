package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils.annotation;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PrivateApi {
    // mark endpoints that don't require JWT
}