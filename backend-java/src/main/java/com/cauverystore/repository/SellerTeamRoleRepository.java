package com.cauverystore.repository;

import com.cauverystore.entities.SellerTeamRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerTeamRoleRepository extends JpaRepository<SellerTeamRole, Long> {

    List<SellerTeamRole> findByBuiltInTrueOrderByIdAsc();

    List<SellerTeamRole> findBySellerIdOrderByIdAsc(Long sellerId);

    Optional<SellerTeamRole> findByBuiltInTrueAndCode(String code);
}
