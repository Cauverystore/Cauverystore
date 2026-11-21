package com.cauverystore.repository;

import com.cauverystore.entities.SupplierOrderTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SupplierOrderTaskRepository extends JpaRepository<SupplierOrderTask, Long> {
    boolean existsByOrderItemId(Long orderItemId);

    List<SupplierOrderTask> findByStatusInOrderByIdDesc(Collection<String> statuses);

    long countByStatus(String status);
}
