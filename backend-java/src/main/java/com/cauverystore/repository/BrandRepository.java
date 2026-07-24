package com.cauverystore.repository;

import com.cauverystore.entities.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findByActiveTrueOrderBySortOrder();
    List<Brand> findAllByOrderBySortOrder();
}
