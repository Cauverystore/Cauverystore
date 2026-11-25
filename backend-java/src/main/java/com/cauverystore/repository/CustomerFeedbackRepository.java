package com.cauverystore.repository;

import com.cauverystore.entities.CustomerFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, Long> {

    /** The list view, without the photo bytes (those are fetched one at a time when opened). */
    interface Row {
        Long getId();
        String getType();
        String getMessage();
        String getName();
        String getEmail();
        String getPageUrl();
        Long getUserId();
        String getPhotoType();
        String getStatus();
        LocalDateTime getCreatedAt();
    }

    @Query("select f.id as id, f.type as type, f.message as message, f.name as name, f.email as email, "
            + "f.pageUrl as pageUrl, f.userId as userId, f.photoType as photoType, f.status as status, "
            + "f.createdAt as createdAt from CustomerFeedback f "
            + "where (:status is null or f.status = :status) and (:type is null or f.type = :type) "
            + "order by f.createdAt desc")
    Page<Row> findRows(@Param("status") String status, @Param("type") String type, Pageable pageable);

    long countByStatus(String status);
}
