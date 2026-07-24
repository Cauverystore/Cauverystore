package com.cauverystore.service;

import com.cauverystore.entities.Warehouse;
import com.cauverystore.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class WarehouseService {
    private final WarehouseRepository warehouseRepo;
    public WarehouseService(WarehouseRepository warehouseRepo) { this.warehouseRepo = warehouseRepo; }

    public List<Warehouse> getAll() { return warehouseRepo.findAll(); }
    public Warehouse getById(Long id) { return warehouseRepo.findById(id).orElse(null); }
    public Warehouse create(Warehouse w) { return warehouseRepo.save(w); }
    public Warehouse update(Long id, Warehouse updated) {
        Warehouse w = warehouseRepo.findById(id).orElse(null);
        if (w == null) return null;
        w.setName(updated.getName()); w.setAddress(updated.getAddress());
        w.setCity(updated.getCity()); w.setState(updated.getState());
        w.setPincode(updated.getPincode()); w.setContactPerson(updated.getContactPerson());
        w.setContactPhone(updated.getContactPhone()); w.setContactEmail(updated.getContactEmail());
        w.setCapacity(updated.getCapacity()); w.setManagerId(updated.getManagerId());
        w.setActive(updated.isActive());
        return warehouseRepo.save(w);
    }
    public void delete(Long id) { warehouseRepo.deleteById(id); }
}
