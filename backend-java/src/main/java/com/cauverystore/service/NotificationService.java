package com.cauverystore.service;

import com.cauverystore.entities.Notification;
import com.cauverystore.entities.Order;
import com.cauverystore.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepo;
    private final EmailService emailService;

    public NotificationService(NotificationRepository notificationRepo, EmailService emailService) {
        this.notificationRepo = notificationRepo;
        this.emailService = emailService;
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

    public void sendOrderPlaced(Order order, double discount) {
        sendSafely(() -> emailService.sendOrderConfirmation(order, discount), "order placed", String.valueOf(order.getId()));
    }

    public void sendPaymentReceived(String email, String orderId) {
        sendSafely(() -> emailService.sendPaymentReceived(email, Map.of("orderId", orderId)), "payment received", orderId);
    }

    public void sendOrderShipped(String email, String orderId) {
        sendSafely(() -> emailService.sendOrderShipped(email, Map.of("orderId", orderId)), "order shipped", orderId);
    }

    public void sendOrderDelivered(String email, String orderId) {
        sendSafely(() -> emailService.sendOrderDelivered(email, Map.of("orderId", orderId)), "order delivered", orderId);
    }

    public void sendOrderCancelled(String email, String orderId) {
        sendSafely(() -> emailService.sendCancellation(email, Map.of("orderId", orderId)), "order cancelled", orderId);
    }

    /** Notification emails are a best-effort side effect and must never fail the order operation that triggered them. */
    private void sendSafely(Runnable action, String kind, String orderId) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Failed to send '{}' notification email for order {}: {}", kind, orderId, e.getMessage(), e);
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
}
