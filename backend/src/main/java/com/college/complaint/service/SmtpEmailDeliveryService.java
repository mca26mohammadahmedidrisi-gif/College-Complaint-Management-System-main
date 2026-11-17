package com.college.complaint.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class SmtpEmailDeliveryService implements EmailDeliveryService {
    private final JavaMailSender sender;
    private final String from;
    private final String frontendUrl;

    public SmtpEmailDeliveryService(JavaMailSender sender,
                                    @Value("${MAIL_FROM:}") String from,
                                    @Value("${app.frontend-url}") String frontendUrl) {
        this.sender = sender;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    @Override public boolean enabled() { return true; }

    @Override
    public void sendVerification(String email, String token) {
        send(email, "Verify your CampusCare account",
                "Verify your account using this link: " + frontendUrl + "/verify-email.html?token=" + token
                        + "\nThis link expires in 24 hours. If you did not register, ignore this email.");
    }

    @Override
    public void sendPasswordReset(String email, String token) {
        send(email, "Reset your CampusCare password",
                "Reset your password using this link: " + frontendUrl + "/reset-password.html?token=" + token
                        + "\nThis link expires in 30 minutes. If you did not request a reset, ignore this email.");
    }

    @Override
    public void sendNotification(String email, String notification) {
        send(email, "CampusCare complaint update", "You have a new CampusCare update:\n\n" + notification
                + "\n\nSign in to CampusCare to view the report.");
    }

    private void send(String recipient, String subject, String text) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            if (from == null || from.isBlank()) throw new IllegalStateException("MAIL_FROM must be configured when email delivery is enabled");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(text, false);
            sender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("The email could not be delivered. Check the backend SMTP configuration.", exception);
        }
    }
}
