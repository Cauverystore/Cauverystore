package com.cauverystore.repository;

import com.cauverystore.entities.CurrencyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyMasterRepository extends JpaRepository<CurrencyMaster, String> {
}
