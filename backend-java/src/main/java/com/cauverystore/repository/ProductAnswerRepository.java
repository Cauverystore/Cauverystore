package com.cauverystore.repository;

import com.cauverystore.entities.ProductAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductAnswerRepository extends JpaRepository<ProductAnswer, Long> {

    List<ProductAnswer> findByQuestion_Id(Long questionId);
}
