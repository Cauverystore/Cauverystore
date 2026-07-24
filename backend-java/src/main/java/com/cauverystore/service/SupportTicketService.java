package com.cauverystore.service;

import com.cauverystore.entities.SupportTicket;
import com.cauverystore.entities.User;
import com.cauverystore.repository.SupportTicketRepository;
import com.cauverystore.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SupportTicketService {
    private final SupportTicketRepository ticketRepo;
    private final UserRepository userRepo;
    public SupportTicketService(SupportTicketRepository ticketRepo, UserRepository userRepo) {
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
    }

    public List<SupportTicket> getAll() { return ticketRepo.findAll(); }
    public SupportTicket getById(Long id) { return ticketRepo.findById(id).orElseThrow(() -> new RuntimeException("Ticket not found")); }
    public List<SupportTicket> getByUser(Long userId) { return ticketRepo.findByUserId(userId); }
    public List<SupportTicket> getByStatus(String status) { return ticketRepo.findByStatus(status); }
    public List<SupportTicket> getByAssignedTo(Long adminId) { return ticketRepo.findByAssignedToId(adminId); }

    public SupportTicket create(SupportTicket ticket, Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        ticket.setUser(user);
        ticket.setStatus("OPEN");
        return ticketRepo.save(ticket);
    }

    public SupportTicket updateStatus(Long id, String status, Long adminId) {
        SupportTicket t = getById(id);
        t.setStatus(status);
        if (adminId != null) {
            User admin = userRepo.findById(adminId).orElseThrow(() -> new RuntimeException("Admin not found"));
            t.setAssignedTo(admin);
        }
        return ticketRepo.save(t);
    }

    public SupportTicket assignTicket(Long id, Long adminId) {
        SupportTicket t = getById(id);
        User admin = userRepo.findById(adminId).orElseThrow(() -> new RuntimeException("Admin not found"));
        t.setAssignedTo(admin);
        return ticketRepo.save(t);
    }

    public void delete(Long id) { ticketRepo.deleteById(id); }
}
