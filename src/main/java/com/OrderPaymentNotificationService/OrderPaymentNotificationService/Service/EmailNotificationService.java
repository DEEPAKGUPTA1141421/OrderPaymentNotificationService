package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

@Service("emailNotificationService")
public class EmailNotificationService implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Override
    public void sendNotification(String to, String subject, String message, File attachment) {
        Email from = new Email(fromEmail);
        Email recipient = new Email(to);
        Content content = new Content("text/html", EmailTemplates.otpEmail(subject, message));
        Mail mail = new Mail(from, subject, recipient, content);

        if (attachment != null && attachment.exists()) {
            try {
                Attachments file = new Attachments();
                file.setContent(Base64.getEncoder().encodeToString(Files.readAllBytes(attachment.toPath())));
                file.setType("application/pdf");
                file.setFilename(attachment.getName());
                file.setDisposition("attachment");
                mail.addAttachments(file);
            } catch (IOException ex) {
                logger.error("Failed to read email attachment {}: {}", attachment.getName(), ex.getMessage());
                throw new RuntimeException("Failed to read email attachment", ex);
            }
        }

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            logger.info("Email sent to {} | Status: {}", to, response.getStatusCode());
        } catch (IOException ex) {
            logger.error("Failed to send email to {}: {}", to, ex.getMessage());
            throw new RuntimeException("Failed to send email", ex);
        }
    }
}
