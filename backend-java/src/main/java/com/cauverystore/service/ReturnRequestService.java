package com.cauverystore.service;

import com.cauverystore.entities.ReturnRequest;
import com.cauverystore.repository.ReturnRequestRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

@Service
public class ReturnRequestService {
    private final ReturnRequestRepository returnRepo;
    private final CreditNoteService creditNoteService;
    public ReturnRequestService(ReturnRequestRepository returnRepo, CreditNoteService creditNoteService) {
        this.returnRepo = returnRepo;
        this.creditNoteService = creditNoteService;
    }

    public List<ReturnRequest> getAll() { return returnRepo.findAll(); }
    public ReturnRequest getById(Long id) { return returnRepo.findById(id).orElse(null); }
    public ReturnRequest create(ReturnRequest rr) { return returnRepo.save(rr); }
    public ReturnRequest updateStatus(Long id, String status) {
        ReturnRequest rr = returnRepo.findById(id).orElse(null);
        if (rr == null) return null;
        rr.setStatus(status);
        ReturnRequest saved = returnRepo.save(rr);

        // Once a return is approved/refunded, auto-generate a credit note reversing GST on the
        // returned quantity (best-effort; a second credit note is not created for the same return).
        if (status != null && CREDIT_NOTE_TRIGGER_STATUSES.contains(status.toUpperCase())) {
            try {
                creditNoteService.generateReturnCreditNote(id, null);
            } catch (Exception e) {
                System.err.println("Auto credit note generation skipped for return " + id + ": " + e.getMessage());
            }
        }
        return saved;
    }
    public List<ReturnRequest> getByStatus(String status) { return returnRepo.findByStatus(status); }
    public List<ReturnRequest> getByOrder(Long orderId) { return returnRepo.findByOrderId(orderId); }
    public List<ReturnRequest> getByUser(Long userId) { return returnRepo.findByUserId(userId); }

    private static final Set<String> CREDIT_NOTE_TRIGGER_STATUSES = Set.of("APPROVED", "REFUNDED", "COMPLETED", "REFUND_ISSUED");
}
