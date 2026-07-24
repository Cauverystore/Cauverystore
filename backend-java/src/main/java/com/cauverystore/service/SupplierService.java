package com.cauverystore.service;

import com.cauverystore.entities.Supplier;
import com.cauverystore.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SupplierService {
    private final SupplierRepository supplierRepo;
    public SupplierService(SupplierRepository supplierRepo) { this.supplierRepo = supplierRepo; }

    public List<Supplier> getAll() { return supplierRepo.findAll(); }
    public Supplier getById(Long id) { return supplierRepo.findById(id).orElse(null); }
    public Supplier create(Supplier s) { return supplierRepo.save(s); }
    public Supplier update(Long id, Supplier updated) {
        Supplier s = supplierRepo.findById(id).orElse(null);
        if (s == null) return null;
        s.setName(updated.getName()); s.setContactPerson(updated.getContactPerson());
        s.setEmail(updated.getEmail()); s.setPhone(updated.getPhone());
        s.setAddress(updated.getAddress()); s.setCity(updated.getCity());
        s.setState(updated.getState()); s.setPincode(updated.getPincode());
        s.setGstin(updated.getGstin()); s.setPaymentTerms(updated.getPaymentTerms());
        s.setLeadTimeDays(updated.getLeadTimeDays());
        s.setPerformanceScore(updated.getPerformanceScore()); s.setActive(updated.isActive());
        return supplierRepo.save(s);
    }
    public void delete(Long id) { supplierRepo.deleteById(id); }
}
