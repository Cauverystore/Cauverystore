package com.cauverystore.repository;

import com.cauverystore.entities.SavedPaymentMethod;
import com.cauverystore.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, Long> {
    List<SavedPaymentMethod> findByUser(User user);
    void deleteByUserAndId(User user, Long id);
}
