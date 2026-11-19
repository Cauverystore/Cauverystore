package com.cauverystore.repository;

import com.cauverystore.entities.SellerReserve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerReserveRepository extends JpaRepository<SellerReserve, Long> { }
