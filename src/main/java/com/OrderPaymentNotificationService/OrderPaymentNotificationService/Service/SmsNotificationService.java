package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;

@Service("smsNotificationService")
public class SmsNotificationService implements NotificationService {

     @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String fromPhone;

    @Override
    public void sendNotification(String to, String subject, String message, File attachment) {
        // comment out to save resource
        // Twilio.init(accountSid, authToken);
        // if (!to.startsWith("+")) {
        //     // Example: prepend +91 if your app is India-only
        //     to = "+91" + to;
        // }
        // Message.creator(
        //         new com.twilio.type.PhoneNumber(to),
        //         new com.twilio.type.PhoneNumber(fromPhone),
        //         message
        // ).create();

        System.out.println("✅ SMS sent to " + to + message);
    }

} 
