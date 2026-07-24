package com.cauverystore.repository;

import com.cauverystore.entities.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByUserId(Long userId);
    List<SupportTicket> findByAssignedToId(Long adminId);
    List<SupportTicket> findByStatus(String status);
}