package com.cauverystore.repository;

import com.cauverystore.entities.SupplierLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierLabelRepository extends JpaRepository<SupplierLabel, Long> {
    Optional<SupplierLabel> findBySkuIgnoreCase(String sku);
}
