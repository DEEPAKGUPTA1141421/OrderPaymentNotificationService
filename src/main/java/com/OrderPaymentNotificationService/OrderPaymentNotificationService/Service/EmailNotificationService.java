package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;


//import com.sendgrid.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

@Service("emailNotificationService")
public class EmailNotificationService implements NotificationService {

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Override
    public void sendNotification(String to, String subject, String message, File attachment) {
        // Email from = new Email(fromEmail);
        // Email recipient = new Email(to);

        // Content content = new Content("text/html", message); // HTML template
        // Mail mail = new Mail(from, subject, recipient, content);

        // SendGrid sg = new SendGrid(sendGridApiKey);
        // Request request = new Request();

        // try {
        //     request.setMethod(Method.POST);
        //     request.setEndpoint("mail/send");
        //     request.setBody(mail.build());
        //     Response response = sg.api(request);
        //     System.out.println("✅ Email sent to " + to + " | Status: " + response.getStatusCode());
        // } catch (IOException ex) {
        //     throw new RuntimeException("Failed to send email", ex);
        // }

        // Attachment handling can be added here if needed
    }
}
