package com.cauverystore.service;

import com.cauverystore.entities.ReturnRequest;
import com.cauverystore.repository.ReturnRequestRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReturnRequestService {
    private final ReturnRequestRepository returnRepo;
    public ReturnRequestService(ReturnRequestRepository returnRepo) { this.returnRepo = returnRepo; }

    public List<ReturnRequest> getAll() { return returnRepo.findAll(); }
    public ReturnRequest getById(Long id) { return returnRepo.findById(id).orElse(null); }
    public ReturnRequest create(ReturnRequest rr) { return returnRepo.save(rr); }
    public ReturnRequest updateStatus(Long id, String status) {
        ReturnRequest rr = returnRepo.findById(id).orElse(null);
        if (rr == null) return null;
        rr.setStatus(status);
        return returnRepo.save(rr);
    }
    public List<ReturnRequest> getByStatus(String status) { return returnRepo.findByStatus(status); }
    public List<ReturnRequest> getByOrder(Long orderId) { return returnRepo.findByOrderId(orderId); }
    public List<ReturnRequest> getByUser(Long userId) { return returnRepo.findByUserId(userId); }
}
