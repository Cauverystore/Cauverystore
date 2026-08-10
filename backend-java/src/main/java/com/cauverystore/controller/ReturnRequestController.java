package com.cauverystore.controller;

import com.cauverystore.entities.Refund;
import com.cauverystore.entities.ReturnRequest;
import com.cauverystore.repository.RefundRepository;
import com.cauverystore.service.PaymentService;
import com.cauverystore.service.ReturnRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/returns")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
public class ReturnRequestController {
    private final ReturnRequestService returnService;
    private final RefundRepository refundRepo;
    private final PaymentService paymentService;

    public ReturnRequestController(ReturnRequestService returnService,
                                   RefundRepository refundRepo,
                                   PaymentService paymentService) {
        this.returnService = returnService;
        this.refundRepo = refundRepo;
        this.paymentService = paymentService;
    }

    @GetMapping public List<ReturnRequest> getAll() { return returnService.getAll(); }
    @GetMapping("/{id}") public ReturnRequest getById(@PathVariable Long id) { return returnService.getById(id); }
    @PutMapping("/{id}/status") public ReturnRequest updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) { return returnService.updateStatus(id, body.get("status")); }
    @GetMapping("/status/{status}") public List<ReturnRequest> getByStatus(@PathVariable String status) { return returnService.getByStatus(status); }

    /**
     * The refunds raised against a return.
     *
     * Separate from the return itself because a refund that failed at the gateway leaves a row
     * behind, and the screen has to show that it failed rather than the return simply looking
     * settled. A return with no refund row is one nobody has paid.
     */
    @GetMapping("/{id}/refunds")
    public List<Refund> refunds(@PathVariable Long id) {
        return refundRepo.findByReturnRequestId(id);
    }

    /**
     * Pays a return the automated attempt could not.
     *
     * The gateway call is retried rather than the amount re-derived: the credit note already
     * settled what is owed, and a manual figure typed here could disagree with the document the
     * return is filed against.
     */
    @PostMapping("/{id}/retry-refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> retryRefund(@PathVariable Long id) {
        ReturnRequest rr = returnService.getById(id);
        if (rr == null) return ResponseEntity.badRequest().body(Map.of("error", "Return not found"));
        if (rr.getRefundAmount() == null || rr.getRefundAmount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "This return has no credited amount yet. It has to pass its quality check first."));
        }
        Long orderId = rr.getOrder() != null ? rr.getOrder().getId() : null;
        if (orderId == null) return ResponseEntity.badRequest().body(Map.of("error", "Return has no linked order"));
        Refund refund = paymentService.processRefundForReturn(id, orderId, rr.getRefundAmount(),
                "Manual retry for RET-" + id, "optimum");
        return ResponseEntity.ok(Map.of(
                "refundId", refund.getId(),
                "status", refund.getStatus(),
                "gatewayRefundId", refund.getGatewayRefundId(),
                "expectedCredit", refund.getExpectedCredit()));
    }
}
