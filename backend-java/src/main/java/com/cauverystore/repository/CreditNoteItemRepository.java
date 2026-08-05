package com.cauverystore.repository;

import com.cauverystore.entities.CreditNoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditNoteItemRepository extends JpaRepository<CreditNoteItem, Long> {
    List<CreditNoteItem> findByCreditNoteId(Long creditNoteId);
}
