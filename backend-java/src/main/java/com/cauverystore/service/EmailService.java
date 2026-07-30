package com.cauverystore.service;

import com.resend.Resend;
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

    private static final String LOGO_URL = "https://cauverystore.in/images/logo.jpg";
    static final String TEAL = "#0E5C5C";
    static final String GOLD = "#C8A24B";
    static final String BEIGE = "#F5F1EA";
    static final String RED = "#D93A2A";

    /** Wraps body HTML with the standard branded header (logo + tagline) and footer divider. */
    static String wrapBranded(String bodyHtml) {
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:" + BEIGE + ";border-radius:8px;overflow:hidden;'>"
                + "<table role='presentation' width='100%' style='background:" + TEAL + ";'><tr><td style='padding:18px 24px;'>"
                + "<table role='presentation'><tr>"
                + "<td valign='middle' style='padding-right:12px;'><img src='" + LOGO_URL + "' alt='Cauvery Store' width='40' style='display:block;border-radius:4px;' /></td>"
                + "<td valign='middle'>"
                + "<div style='color:#ffffff;font-size:18px;font-weight:bold;'>Cauvery Store</div>"
                + "<div style='color:" + GOLD + ";font-size:11px;'>Everyday Essentials, Delivered</div>"
                + "</td></tr></table>"
                + "</td></tr></table>"
                + "<div style='padding:24px;background:#ffffff;'>" + bodyHtml + "</div>"
                + "<div style='height:4px;background:" + GOLD + ";'></div>"
                + "<div style='padding:12px 24px;font-size:11px;color:#6b7280;text-align:center;'>&copy; Cauvery Store</div>"
                + "</div>";
    }

    public void sendOtpEmail(String to, String otp) {
        String subject = "Your OTP for Password Reset - Cauvery Store";
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Password Reset OTP</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Use the following OTP to reset your password. This OTP is valid for 15 minutes.</p>"
                + "<div style='background:" + BEIGE + ";padding:20px;border-radius:6px;text-align:center;margin:20px 0;'>"
                + "<span style='font-size:32px;font-weight:bold;letter-spacing:8px;color:" + TEAL + ";'>" + otp + "</span></div>"
                + "<p style='color:#9ca3af;font-size:12px;'>If you did not request this, please ignore this email.</p>";
        send(to, subject, wrapBranded(body));
    }

    public void sendPasswordResetLink(String to, String resetUrl) {
        String subject = "Reset Your Password - Cauvery Store";
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Reset Your Password</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Click the button below to choose a new password. This link is valid for 30 minutes and can only be used once.</p>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='" + resetUrl + "' style='background:" + TEAL + ";color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;font-weight:bold;'>Reset Password</a></div>"
                + "<p style='color:#9ca3af;font-size:12px;'>If you did not request this, please ignore this email. If the button doesn't work, copy and paste this link: " + resetUrl + "</p>";
        send(to, subject, wrapBranded(body));
    }

    public void sendWelcomeEmail(String to, String name) {
        String subject = "Welcome to Cauvery Store!";
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Welcome, " + name + "!</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Thank you for joining Cauvery Store. Start exploring our wide range of products!</p>"
                + "<div style='text-align:center;margin:20px 0;'>"
                + "<a href='https://cauverystore.in' style='background:" + TEAL + ";color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;font-weight:bold;'>Browse Products</a></div>";
        send(to, subject, wrapBranded(body));
    }

    public void sendPasswordResetConfirmation(String to) {
        String subject = "Your Password Has Been Changed - Cauvery Store";
        String changedAt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                .format(java.time.LocalDateTime.now());
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Password Changed</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Your password was changed at <strong>" + changedAt + "</strong>. "
                + "If this wasn't you, please contact support immediately.</p>";
        send(to, subject, wrapBranded(body));
    }

    public void sendOrderConfirmation(String to, Map<String, Object> orderData) {
        String subject = "Order Confirmed - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Order Confirmed!</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Your order <strong>#" + orderId + "</strong> has been confirmed.</p>"
                + "<p style='color:#4b5563;font-size:14px;'>Thank you for shopping with Cauvery Store.</p>";
        send(to, subject, wrapBranded(body));
    }

    public void sendOrderShipped(String to, Map<String, Object> orderData) {
        String subject = "Order Shipped - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Your Order Has Been Shipped!</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Order <strong>#" + orderId + "</strong> is on its way.</p>";
        send(to, subject, wrapBranded(body));
    }

    public void sendOrderDelivered(String to, Map<String, Object> orderData) {
        String subject = "Order Delivered - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Order Delivered!</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Order <strong>#" + orderId + "</strong> has been delivered. Enjoy your purchase!</p>";
        send(to, subject, wrapBranded(body));
    }

    public void sendPaymentReceived(String to, Map<String, Object> orderData) {
        String subject = "Payment Received - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Payment Received</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>We've received your payment for order <strong>#" + orderId + "</strong>.</p>"
                + "<p style='color:#4b5563;font-size:14px;'>Thank you for shopping with Cauvery Store.</p>";
        send(to, subject, wrapBranded(body));
    }

    public void sendCancellation(String to, Map<String, Object> orderData) {
        String subject = "Order Cancelled - Cauvery Store";
        String orderId = orderData.getOrDefault("orderId", "").toString();
        String body = "<h2 style='color:" + RED + ";margin-top:0;'>Order Cancelled</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>Order <strong>#" + orderId + "</strong> has been cancelled as requested.</p>";
        send(to, subject, wrapBranded(body));
    }

    public void sendRefundConfirmation(String to, Map<String, Object> refundData) {
        String subject = "Refund Processed - Cauvery Store";
        String amount = refundData.getOrDefault("amount", "0").toString();
        String body = "<h2 style='color:" + TEAL + ";margin-top:0;'>Refund Processed</h2>"
                + "<p style='color:#4b5563;font-size:14px;'>A refund of <strong>\u20B9" + amount + "</strong> has been processed.</p>";
        send(to, subject, wrapBranded(body));
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
        } catch (Exception e) {
            log.error("Failed to send email to {} (subject: {}): {}", to, subject, e.getMessage(), e);
        }
    }
}
