package com.cauverystore.repository;

import com.cauverystore.entities.InboundEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InboundEmailRepository extends JpaRepository<InboundEmail, Long> {
    boolean existsByResendEmailId(String resendEmailId);
    Page<InboundEmail> findAllByOrderByReceivedAtDesc(Pageable pageable);
    long countByReadFalse();

    List<InboundEmail> findByAutoReplyStatusIsNullAndReceivedAtAfterOrderByIdAsc(LocalDateTime after, Pageable pageable);

    long countByFromAddressIgnoreCaseAndAutoReplyStatusAndAutoRepliedAtAfter(String fromAddress, String status, LocalDateTime after);

    /** Returns 1 only for the one caller that gets to handle this email. */
    @Modifying
    @Query("update InboundEmail e set e.autoReplyStatus = :status where e.id = :id and e.autoReplyStatus is null")
    int claimForAutoReply(@Param("id") Long id, @Param("status") String status);

    @Modifying
    @Query("update InboundEmail e set e.autoReplyStatus = :status, e.autoReplyReason = :reason, e.autoReplyText = :text, "
            + "e.autoRepliedAt = :at where e.id = :id")
    int recordAutoReply(@Param("id") Long id, @Param("status") String status, @Param("reason") String reason,
                        @Param("text") String text, @Param("at") LocalDateTime at);
}
