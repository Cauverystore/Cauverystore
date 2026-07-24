package com.cauverystore.service;

import com.cauverystore.entities.Notification;
import com.cauverystore.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepo;

    @Autowired(required = false)
    private ResendEmailService resendEmailService;

    public NotificationService(NotificationRepository notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    public Notification createNotification(Long userId, String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepo.save(notification);
    }

    public void sendOrderPlaced(String email, String orderId) {
        String html = buildEmailHtml("Order Placed",
                "<h2>Your order #" + orderId + " has been placed successfully!</h2>" +
                "<p>We will notify you once it is shipped.</p>");
        sendEmailIfAvailable(email, "Order Placed - #" + orderId, html);
    }

    public void sendPaymentReceived(String email, String orderId) {
        String html = buildEmailHtml("Payment Received",
                "<h2>Payment received for order #" + orderId + "</h2>" +
                "<p>Thank you for your payment.</p>");
        sendEmailIfAvailable(email, "Payment Received - #" + orderId, html);
    }

    public void sendOrderShipped(String email, String orderId) {
        String html = buildEmailHtml("Order Shipped",
                "<h2>Your order #" + orderId + " is on its way!</h2>" +
                "<p>Track your order for updates.</p>");
        sendEmailIfAvailable(email, "Order Shipped - #" + orderId, html);
    }

    public void sendOrderDelivered(String email, String orderId) {
        String html = buildEmailHtml("Order Delivered",
                "<h2>Your order #" + orderId + " has been delivered!</h2>" +
                "<p>We hope you enjoy your purchase.</p>");
        sendEmailIfAvailable(email, "Order Delivered - #" + orderId, html);
    }

    public void sendOrderCancelled(String email, String orderId) {
        String html = buildEmailHtml("Order Cancelled",
                "<h2>Your order #" + orderId + " has been cancelled.</h2>" +
                "<p>If you have any questions, please contact support.</p>");
        sendEmailIfAvailable(email, "Order Cancelled - #" + orderId, html);
    }

    private void sendEmailIfAvailable(String to, String subject, String html) {
        if (resendEmailService != null) {
            try {
                resendEmailService.sendHtml(to, subject, html);
            } catch (Exception e) {
                // Email sending failed — log and continue
            }
        }
    }

    public java.util.List<Notification> getNotifications(Long userId) {
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepo.countByUserIdAndReadFalse(userId);
    }

    public Notification markRead(Long id, Long userId) {
        Notification n = notificationRepo.findById(id).orElseThrow();
        if (!n.getUserId().equals(userId)) throw new RuntimeException("Notification not found");
        n.setRead(true);
        return notificationRepo.save(n);
    }

    public void markAllRead(Long userId) {
        java.util.List<Notification> list = notificationRepo.findByUserIdOrderByCreatedAtDesc(userId);
        for (Notification n : list) {
            n.setRead(true);
        }
        notificationRepo.saveAll(list);
    }

    public void deleteNotification(Long id, Long userId) {
        Notification n = notificationRepo.findById(id).orElseThrow();
        if (!n.getUserId().equals(userId)) throw new RuntimeException("Notification not found");
        notificationRepo.deleteById(id);
    }

    private String buildEmailHtml(String title, String body) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>" +
                "<body style=\"font-family:Arial,sans-serif;padding:20px;\">" +
                "<div style=\"max-width:600px;margin:0 auto;border:1px solid #ddd;border-radius:8px;overflow:hidden;\">" +
                "<div style=\"background:#1a73e8;color:#fff;padding:20px;text-align:center;\">" +
                "<h1>Cauvery Store</h1></div>" +
                "<div style=\"padding:20px;\">" + body + "</div>" +
                "<div style=\"background:#f5f5f5;padding:15px;text-align:center;font-size:12px;color:#666;\">" +
                "<p>&copy; 2026 Cauvery Store. All rights reserved.</p></div>" +
                "</div></body></html>";
    }
}
