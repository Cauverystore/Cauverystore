package com.cauverystore.repository;

import com.cauverystore.entities.PortMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortMasterRepository extends JpaRepository<PortMaster, String> {
}
