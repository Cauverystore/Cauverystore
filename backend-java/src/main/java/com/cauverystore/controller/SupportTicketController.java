package com.cauverystore.controller;

import com.cauverystore.entities.SupportTicket;
import com.cauverystore.service.SupportTicketService;
import com.cauverystore.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support-tickets")
public class SupportTicketController {
    private final SupportTicketService ticketService;
    private final AuthService authService;
    public SupportTicketController(SupportTicketService ticketService, AuthService authService) {
        this.ticketService = ticketService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<SupportTicket>> getMyTickets() {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(ticketService.getByUser(userId));
    }

    @PostMapping
    public ResponseEntity<SupportTicket> create(@RequestBody SupportTicket ticket) {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(ticketService.create(ticket, userId));
    }
}
