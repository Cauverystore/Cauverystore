package com.cauverystore.repository;

import com.cauverystore.entities.DebitNoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DebitNoteItemRepository extends JpaRepository<DebitNoteItem, Long> {
    List<DebitNoteItem> findByDebitNoteId(Long debitNoteId);
}
