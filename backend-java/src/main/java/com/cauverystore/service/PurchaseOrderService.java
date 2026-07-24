package com.cauverystore.service;

import com.cauverystore.entities.PurchaseOrder;
import com.cauverystore.repository.PurchaseOrderRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderRepository poRepo;
    public PurchaseOrderService(PurchaseOrderRepository poRepo) { this.poRepo = poRepo; }

    public List<PurchaseOrder> getAll() { return poRepo.findAll(); }
    public PurchaseOrder getById(Long id) { return poRepo.findById(id).orElse(null); }
    public PurchaseOrder create(PurchaseOrder po) { return poRepo.save(po); }
    public PurchaseOrder update(Long id, PurchaseOrder updated) {
        PurchaseOrder po = poRepo.findById(id).orElse(null);
        if (po == null) return null;
        po.setStatus(updated.getStatus()); po.setExpectedDelivery(updated.getExpectedDelivery());
        po.setActualDelivery(updated.getActualDelivery()); po.setNotes(updated.getNotes());
        po.setTotalAmount(updated.getTotalAmount());
        return poRepo.save(po);
    }
    public void delete(Long id) { poRepo.deleteById(id); }
    public List<PurchaseOrder> getByStatus(String status) { return poRepo.findByStatus(status); }
    public List<PurchaseOrder> getBySupplier(Long supplierId) { return poRepo.findBySupplierId(supplierId); }
}
