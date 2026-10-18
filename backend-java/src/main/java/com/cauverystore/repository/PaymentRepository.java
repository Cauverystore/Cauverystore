package com.cauverystore.repository;

import com.cauverystore.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findByOrderId(Long orderId);

    /** Money taken that never turned into an order - see PaymentService.refundUnclaimedPayments. */
    List<Payment> findByOrderIdIsNullAndStatusInAndPaidAtBefore(Collection<String> statuses, LocalDateTime cutoff);
}
