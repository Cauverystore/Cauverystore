package com.cauverystore.service;

import com.cauverystore.entities.Notification;
import com.cauverystore.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class NotificationService {

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

    public void sendOrderPlaced(String email, String orderId) {
        emailService.sendOrderConfirmation(email, Map.of("orderId", orderId));
    }

    public void sendPaymentReceived(String email, String orderId) {
        emailService.sendPaymentReceived(email, Map.of("orderId", orderId));
    }

    public void sendOrderShipped(String email, String orderId) {
        emailService.sendOrderShipped(email, Map.of("orderId", orderId));
    }

    public void sendOrderDelivered(String email, String orderId) {
        emailService.sendOrderDelivered(email, Map.of("orderId", orderId));
    }

    public void sendOrderCancelled(String email, String orderId) {
        emailService.sendCancellation(email, Map.of("orderId", orderId));
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
