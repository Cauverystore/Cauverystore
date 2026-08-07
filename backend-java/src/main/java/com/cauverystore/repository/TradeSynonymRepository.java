package com.cauverystore.repository;

import com.cauverystore.entities.TradeSynonym;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeSynonymRepository extends JpaRepository<TradeSynonym, Long> {

    Optional<TradeSynonym> findByTermIgnoreCase(String term);

    List<TradeSynonym> findAllByOrderByTermAsc();
}
