package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.phonepe.sdk.pg.Env;
import com.phonepe.sdk.pg.payments.v2.StandardCheckoutClient;
import com.phonepe.sdk.pg.payments.v2.models.request.CreateSdkOrderRequest;
import com.phonepe.sdk.pg.payments.v2.models.response.CreateSdkOrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.phonepe.sdk.pg.common.models.response.OrderStatusResponse;
import com.phonepe.sdk.pg.common.models.request.RefundRequest;
import com.phonepe.sdk.pg.common.models.response.RefundResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PhonePeServiceImpl implements PhonePeService {

        @Value("${phonepe.clientId}")
        private String clientId;

        @Value("${phonepe.clientSecret}")
        private String clientSecret;

        @Value("${phonepe.clientVersion}")
        private Integer clientVersion;

        // Set environment: SANDBOX or PRODUCTION
        private static final Env ENVIRONMENT = Env.SANDBOX;

        @Override
        public Map<String, Object> createOrder(String merchantOrderId, String amount, String idempotencyKey) {
                try {

                        // ✅ Build request (amount in paise)
                        long paymentAmount = (long) (Double.parseDouble(amount) * 100);
                        String redirectUrl = "https://your-frontend.com/payment/success";
                        System.out
                                        .println("check parameter become making call " + merchantOrderId + " "
                                                        + paymentAmount + " "
                                                        + redirectUrl);
                        // ✅ Initialize PhonePe client
                        StandardCheckoutClient client = StandardCheckoutClient.getInstance(clientId, clientSecret,
                                        clientVersion, ENVIRONMENT);
                        CreateSdkOrderRequest request = CreateSdkOrderRequest.StandardCheckoutBuilder()
                                        .merchantOrderId(merchantOrderId)
                                        .amount(paymentAmount)
                                        .redirectUrl(redirectUrl)
                                        .build();
                        System.out
                                        .println("Request is " + request.toString());
                        // ✅ Call PhonePe SDK to create order
                        CreateSdkOrderResponse response = client.createSdkOrder(request);
                        System.out
                                        .println("Response is " + response.toString());
                        // ✅ Return structured response
                        Map<String, Object> result = new HashMap<>();
                        result.put("orderId", response.getOrderId());
                        result.put("token", response.getToken());
                        result.put("merchantId", merchantOrderId);
                        result.put("status", "IniTiated");
                        return result;
                } catch (Exception e) {
                        throw new RuntimeException("Failed to create PhonePe order: " + e.getMessage());
                }
        }

        @Override
        public Map<String, Object> checkOrderStatus(String merchantOrderId) {
                // ✅ Initialize PhonePe client
                StandardCheckoutClient client = StandardCheckoutClient.getInstance(clientId, clientSecret,
                                clientVersion, ENVIRONMENT);
                OrderStatusResponse orderStatusResponse = client.getOrderStatus(merchantOrderId);
                Map<String, Object> result = new HashMap<>();
                result.put("orderId", orderStatusResponse.getOrderId());
                result.put("state", orderStatusResponse.getState());
                result.put("expireAt", orderStatusResponse.getExpireAt());
                result.put("errorCode", orderStatusResponse.getErrorCode());
                return result;
        }

        @Override
        public Map<String, Object> refundPayment(String merchantOrderId, long amount) {
                // ✅ Initialize PhonePe client
                StandardCheckoutClient client = StandardCheckoutClient.getInstance(clientId, clientSecret,
                                clientVersion, ENVIRONMENT);
                return Map.of("name", "name");
        }
}

// hyuy7iu huhuiy yy78yhyuy7ui yuy78 yuy78u8 t6uredtryghb gyuty6
// iuu jiok ilkoklkol olkokol kikoklklkl
// hhukj kuhjhjjkjnjnnjnjnjnnjnjnjnjhkījujuju
// huuij uioiouiuiuiu jii uuijiji ijijji jijijkhuhuhh
// huhuhhuhjhjhjjkjhhjhjhuyuyuhhuhjkjkmuiujujjuijijiij
// juiuoiiojioji iikk ijkklk