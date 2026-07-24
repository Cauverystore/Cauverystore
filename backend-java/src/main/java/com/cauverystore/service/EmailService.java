package com.cauverystore.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${resend.api.key:}")
    private String apiKey;

    @Value("${resend.from.email:Cauvery Store <noreply@cauverystore.in>}")
    private String fromEmail;

    private Resend resend;
    private boolean configured = false;

    @PostConstruct
    void init() {
        if (apiKey != null && !apiKey.isEmpty() && !apiKey.contains("placeholder") && !apiKey.equals("re_placeholder")) {
            resend = new Resend(apiKey);
            configured = true;
        }
    }

    public void sendOtpEmail(String to, String otp) {
        String subject = "Your OTP for Password Reset - Cauvery Store";
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9fafb;border-radius:8px;'>"
                + "<h2 style='color:#1f2937;'>Password Reset OTP</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Use the following OTP to reset your password. This OTP is valid for 15 minutes.</p>"
                + "<div style='background:#ffffff;padding:20px;border-radius:6px;text-align:center;margin:20px 0;'>"
                + "<span style='font-size:32px;font-weight:bold;letter-spacing:8px;color:#6366f1;'>" + otp + "</span></div>"
                + "<p style='color:#9ca3af;font-size:12px;'>If you did not request this, please ignore this email.</p></div>";
        send(to, subject, html);
    }

    public void sendWelcomeEmail(String to, String name) {
        String subject = "Welcome to Cauvery Store!";
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9fafb;border-radius:8px;'>"
                + "<h2 style='color:#1f2937;'>Welcome, " + name + "!</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Thank you for joining Cauvery Store. Start exploring our wide range of products!</p>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='https://cauverystore.in' style='background:#6366f1;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;'>Browse Products</a></div></div>";
        send(to, subject, html);
    }

    public void sendPasswordResetConfirmation(String to) {
        String subject = "Your Password Has Been Reset - Cauvery Store";
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9fafb;border-radius:8px;'>"
                + "<h2 style='color:#1f2937;'>Password Reset Successful</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Your password has been successfully reset. If you did not perform this action, please contact support immediately.</p></div>";
        send(to, subject, html);
    }

    public void sendOrderConfirmation(String to, Map<String, Object> orderData) {
        String subject = "Order Confirmed - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9fafb;border-radius:8px;'>"
                + "<h2 style='color:#1f2937;'>Order Confirmed!</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Your order <strong>#" + orderId + "</strong> has been confirmed.</p>"
                + "<p style='color:#4b5563;font-size:14px;'>Thank you for shopping with Cauvery Store.</p></div>";
        send(to, subject, html);
    }

    public void sendOrderShipped(String to, Map<String, Object> orderData) {
        String subject = "Order Shipped - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9fafb;border-radius:8px;'>"
                + "<h2 style='color:#1f2937;'>Your Order Has Been Shipped!</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Order <strong>#" + orderId + "</strong> is on its way.</p></div>";
        send(to, subject, html);
    }

    public void sendOrderDelivered(String to, Map<String, Object> orderData) {
        String subject = "Order Delivered - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9fafb;border-radius:8px;'>"
                + "<h2 style='color:#1f2937;'>Order Delivered!</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Order <strong>#" + orderId + "</strong> has been delivered. Enjoy your purchase!</p></div>";
        send(to, subject, html);
    }

    public void sendCancellation(String to, Map<String, Object> orderData) {
        String subject = "Order Cancelled - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9fafb;border-radius:8px;'>"
                + "<h2 style='color:#1f2937;'>Order Cancelled</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Order <strong>#" + orderId + "</strong> has been cancelled as requested.</p></div>";
        send(to, subject, html);
    }

    public void sendRefundConfirmation(String to, Map<String, Object> refundData) {
        String subject = "Refund Processed - Cauvery Store";
        String amount = refundData.getOrDefault("amount", "0").toString();
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9fafb;border-radius:8px;'>"
                + "<h2 style='color:#1f2937;'>Refund Processed</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>A refund of <strong>\u20B9" + amount + "</strong> has been processed.</p></div>";
        send(to, subject, html);
    }

    private void send(String to, String subject, String html) {
        if (!configured) {
            log.warn("Email not configured. Would have sent to {} with subject: {}", to, subject);
            return;
        }
        try {
            CreateEmailOptions request = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(to)
                    .subject(subject)
                    .html(html)
                    .build();
            resend.emails().send(request);
            log.info("Email sent to {} with subject: {}", to, subject);
        } catch (ResendException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
