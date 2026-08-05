package com.cauverystore.controller;

import com.cauverystore.entities.SupportTicket;
import com.cauverystore.service.SupportTicketService;
import com.cauverystore.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/support-tickets")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminSupportTicketController {
    private final SupportTicketService ticketService;
    private final AuthService authService;
    public AdminSupportTicketController(SupportTicketService ticketService, AuthService authService) {
        this.ticketService = ticketService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<SupportTicket>> getAll() { return ResponseEntity.ok(ticketService.getAll()); }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicket> getById(@PathVariable Long id) { return ResponseEntity.ok(ticketService.getById(id)); }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<SupportTicket>> getByStatus(@PathVariable String status) { return ResponseEntity.ok(ticketService.getByStatus(status)); }

    @PutMapping("/{id}/status")
    public ResponseEntity<SupportTicket> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long adminId = authService.deriveUserId();
        return ResponseEntity.ok(ticketService.updateStatus(id, body.get("status"), adminId));
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<SupportTicket> assign(@PathVariable Long id) {
        Long adminId = authService.deriveUserId();
        return ResponseEntity.ok(ticketService.assignTicket(id, adminId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { ticketService.delete(id); return ResponseEntity.noContent().build(); }
}
