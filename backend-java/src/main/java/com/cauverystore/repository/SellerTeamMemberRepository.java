package com.cauverystore.repository;

import com.cauverystore.entities.SellerTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerTeamMemberRepository extends JpaRepository<SellerTeamMember, Long> {

    List<SellerTeamMember> findBySellerIdOrderByIdAsc(Long sellerId);

    List<SellerTeamMember> findByUserIdAndStatus(Long userId, String status);

    boolean existsByUserIdAndStatus(Long userId, String status);

    Optional<SellerTeamMember> findBySellerIdAndUserId(Long sellerId, Long userId);

    Optional<SellerTeamMember> findBySellerIdAndUserIdAndStatus(Long sellerId, Long userId, String status);

    long countByRoleIdAndStatus(Long roleId, String status);

    long countBySellerIdAndStatus(Long sellerId, String status);
}
