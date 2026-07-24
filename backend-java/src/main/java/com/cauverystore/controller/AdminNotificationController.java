package com.cauverystore.controller;

import com.cauverystore.entities.Notification;
import com.cauverystore.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminNotificationController {
    private final NotificationRepository notificationRepo;
    public AdminNotificationController(NotificationRepository notificationRepo) { this.notificationRepo = notificationRepo; }

    @GetMapping
    public ResponseEntity<List<Notification>> getAll() {
        return ResponseEntity.ok(notificationRepo.findAllByOrderByCreatedAtDesc());
    }
}
