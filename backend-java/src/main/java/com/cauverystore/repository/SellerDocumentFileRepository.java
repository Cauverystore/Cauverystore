package com.cauverystore.repository;

import com.cauverystore.entities.SellerDocumentFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerDocumentFileRepository extends JpaRepository<SellerDocumentFile, Long> {
}
