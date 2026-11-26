package com.cauverystore.billinglink;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingLinkPurchaseRepository extends JpaRepository<BillingLinkPurchase, Long> {
    Optional<BillingLinkPurchase> findByPurchaseOrderId(Long purchaseOrderId);

    long countByStatus(String status);
}
