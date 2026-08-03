package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** Full Amazon-style order confirmation: order summary, itemized products, shipping info, and charges breakdown. */
    public void sendOrderConfirmation(Order order, double discount) {
        if (order.getUser() == null || order.getUser().getEmail() == null) return;
        String to = order.getUser().getEmail();
        String customerName = order.getUser().getFullName() != null ? order.getUser().getFullName() : "there";
        String orderId = String.valueOf(order.getId());
        String orderDate = order.getCreatedAt() != null ? order.getCreatedAt().format(EMAIL_DATE_FORMAT) : "-";

        String subject = "Your CauveryStore Order #" + orderId + " Has Been Confirmed";

        StringBuilder body = new StringBuilder();
        body.append("<h2 style='color:").append(TEAL).append(";margin-top:0;'>Order Confirmed!</h2>")
                .append("<p style='color:#4b5563;font-size:14px;'>Hello ").append(customerName)
                .append(", thank you for shopping with us.</p>");

        // Order summary
        body.append("<table role='presentation' width='100%' style='font-size:13px;color:#4b5563;margin:16px 0;'>")
                .append(summaryRow("Order ID", "#" + orderId))
                .append(summaryRow("Order Date", orderDate))
                .append(summaryRow("Payment Method", safe(order.getPaymentMethod())))
                .append(summaryRow("Billing & Shipping Address", formatAddress(order.getAddress())))
                .append("</table>");

        // Items purchased
        body.append("<h3 style='color:").append(TEAL).append(";font-size:15px;margin-bottom:8px;'>Items Purchased</h3>")
                .append("<table role='presentation' width='100%' style='border-collapse:collapse;font-size:13px;'>")
                .append("<tr style='background:").append(TEAL).append(";color:#ffffff;'>")
                .append("<th style='padding:8px;text-align:left;'>Product</th>")
                .append("<th style='padding:8px;text-align:center;'>Qty</th>")
                .append("<th style='padding:8px;text-align:right;'>Price</th>")
                .append("<th style='padding:8px;text-align:right;'>Subtotal</th></tr>");
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                String name = item.getProduct() != null ? item.getProduct().getName() : "Item";
                double lineSubtotal = item.getPrice() * item.getQuantity();
                body.append("<tr style='border-bottom:1px solid #e5e7eb;'>")
                        .append("<td style='padding:8px;'>").append(safe(name)).append("</td>")
                        .append("<td style='padding:8px;text-align:center;'>").append(item.getQuantity()).append("</td>")
                        .append("<td style='padding:8px;text-align:right;'>₹").append(String.format("%.2f", item.getPrice())).append("</td>")
                        .append("<td style='padding:8px;text-align:right;'>₹").append(String.format("%.2f", lineSubtotal)).append("</td>")
                        .append("</tr>");
            }
        }
        body.append("</table>");

        // Shipping information
        Address addr = order.getAddress();
        String estimatedDelivery = order.getCreatedAt() != null
                ? order.getCreatedAt().plusDays(5).format(EMAIL_DATE_FORMAT) + " - " + order.getCreatedAt().plusDays(7).format(EMAIL_DATE_FORMAT)
                : "5-7 business days";
        body.append("<h3 style='color:").append(TEAL).append(";font-size:15px;margin:20px 0 8px;'>Shipping Information</h3>")
                .append("<table role='presentation' width='100%' style='font-size:13px;color:#4b5563;'>")
                .append(summaryRow("Ship To", (addr != null ? safe(addr.getFullName()) + ", " + formatAddress(addr) + ", " + safe(addr.getPhone()) : "-")))
                .append(summaryRow("Estimated Delivery", estimatedDelivery))
                .append(summaryRow("Courier Partner", order.getCourier() != null ? safe(order.getCourier()) : "Will be assigned before shipping"))
                .append(summaryRow("Tracking ID", order.getTrackingNumber() != null ? safe(order.getTrackingNumber()) : "Tracking details will be shared once shipped"))
                .append("</table>");

        // Charges breakdown
        Double subTotal = order.getSubTotal();
        Double tax = order.getTax();
        Double deliveryCharge = order.getDeliveryCharge();
        body.append("<h3 style='color:").append(TEAL).append(";font-size:15px;margin:20px 0 8px;'>Charges Breakdown</h3>")
                .append("<table role='presentation' width='100%' style='font-size:13px;color:#4b5563;'>")
                .append(summaryRow("Subtotal", "₹" + String.format("%.2f", subTotal != null ? subTotal : 0)))
                .append(summaryRow("Shipping Charges", deliveryCharge != null && deliveryCharge > 0 ? "₹" + String.format("%.2f", deliveryCharge) : "FREE"))
                .append(summaryRow("Taxes/GST", "₹" + String.format("%.2f", tax != null ? tax : 0)));
        if (discount > 0) {
            body.append(summaryRow("Discount", "-₹" + String.format("%.2f", discount)));
        }
        body.append("<tr style='border-top:2px solid ").append(TEAL).append(";font-weight:bold;color:").append(TEAL).append(";'>")
                .append("<td style='padding:8px 4px;'>Grand Total</td><td style='padding:8px 4px;text-align:right;'>₹")
                .append(String.format("%.2f", order.getTotalAmount())).append("</td></tr>")
                .append("</table>");

        // Next steps
        body.append("<p style='color:#4b5563;font-size:13px;margin-top:20px;'>We'll notify you once your order has been shipped. "
                + "You can track your order anytime from your CauveryStore account.</p>");

        // Customer support
        body.append("<div style='background:").append(BEIGE).append(";padding:14px;border-radius:6px;margin-top:16px;font-size:12px;color:#4b5563;'>")
                .append("<strong>Need help?</strong><br/>")
                .append("Email: <a href='mailto:support@cauverystore.in'>support@cauverystore.in</a><br/>")
                .append("Phone: <a href='tel:+919876543210'>+91 98765 43210</a><br/>")
                .append("<a href='https://cauverystore.in/refund-policy'>Returns &amp; Refund Policy</a> &middot; "
                        + "<a href='https://cauverystore.in/help'>Help Center</a>")
                .append("</div>")
                .append("<p style='color:#9ca3af;font-size:12px;margin-top:16px;'>Thank you for choosing CauveryStore!</p>");

        send(to, subject, wrapBranded(body.toString()));
    }

    private static String summaryRow(String label, String value) {
        return "<tr><td style='padding:4px 8px 4px 0;color:#9ca3af;white-space:nowrap;vertical-align:top;'>" + label
                + "</td><td style='padding:4px 0;font-weight:600;'>" + value + "</td></tr>";
    }

    private static String formatAddress(Address addr) {
        if (addr == null) return "-";
        return String.join(", ",
                safe(addr.getStreet()), safe(addr.getCity()), safe(addr.getState()) + " - " + safe(addr.getPincode()));
    }

    private static String safe(String s) {
        return s != null && !s.isBlank() ? s : "-";
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
