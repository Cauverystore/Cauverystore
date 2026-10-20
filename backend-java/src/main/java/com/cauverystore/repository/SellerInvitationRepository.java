package com.cauverystore.repository;

import com.cauverystore.entities.SellerInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerInvitationRepository extends JpaRepository<SellerInvitation, Long> {

    Optional<SellerInvitation> findByTokenHash(String tokenHash);

    List<SellerInvitation> findBySellerIdAndStatusOrderByIdDesc(Long sellerId, String status);

    List<SellerInvitation> findBySellerIdAndEmailIgnoreCaseAndStatus(Long sellerId, String email, String status);

    long countByRoleIdAndStatus(Long roleId, String status);
}
