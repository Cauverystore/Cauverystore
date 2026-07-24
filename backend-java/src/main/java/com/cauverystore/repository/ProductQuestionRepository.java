package com.cauverystore.repository;

import com.cauverystore.entities.ProductQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductQuestionRepository extends JpaRepository<ProductQuestion, Long> {

    List<ProductQuestion> findByProduct_Id(Long productId);
}
