package com.cauverystore.repository;

import com.cauverystore.entities.UnitMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitMasterRepository extends JpaRepository<UnitMaster, String> {
}
