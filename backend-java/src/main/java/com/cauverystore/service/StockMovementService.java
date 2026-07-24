package com.cauverystore.service;

import com.cauverystore.entities.StockMovement;
import com.cauverystore.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StockMovementService {
    private final StockMovementRepository repo;
    public StockMovementService(StockMovementRepository repo) { this.repo = repo; }

    public List<StockMovement> getAll() { return repo.findAll(); }
    public StockMovement getById(Long id) { return repo.findById(id).orElseThrow(() -> new RuntimeException("Movement not found")); }
    public List<StockMovement> getByProduct(Long productId) { return repo.findByProductIdOrderByCreatedAtDesc(productId); }
    public List<StockMovement> getByWarehouse(Long warehouseId) { return repo.findByWarehouseIdOrderByCreatedAtDesc(warehouseId); }
    public List<StockMovement> getByType(String type) { return repo.findByType(type); }
    public StockMovement create(StockMovement m) { return repo.save(m); }
}
