package com.cauverystore.repository;

import com.cauverystore.entities.SellerBrandApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerBrandApplicationRepository extends JpaRepository<SellerBrandApplication, Long> {

    List<SellerBrandApplication> findBySellerIdOrderByIdDesc(Long sellerId);

    List<SellerBrandApplication> findByStatusInOrderByIdAsc(List<String> statuses);

    List<SellerBrandApplication> findAllByOrderByIdDesc();

    List<SellerBrandApplication> findBySellerIdAndBrandNameIgnoreCaseAndStatusIn(Long sellerId, String brandName, List<String> statuses);

    boolean existsBySellerIdAndBrandIdAndStatus(Long sellerId, Long brandId, String status);
}
