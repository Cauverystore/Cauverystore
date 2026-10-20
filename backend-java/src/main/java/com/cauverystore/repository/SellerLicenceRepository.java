package com.cauverystore.repository;

import com.cauverystore.entities.SellerLicence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerLicenceRepository extends JpaRepository<SellerLicence, Long> {

    List<SellerLicence> findBySellerIdOrderByIdAsc(Long sellerId);

    List<SellerLicence> findBySellerIdAndStatusOrderByIdAsc(Long sellerId, String status);
}
