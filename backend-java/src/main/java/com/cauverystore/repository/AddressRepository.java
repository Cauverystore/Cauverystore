package com.cauverystore.repository;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUser(User user);
    List<Address> findByUserAndActiveFlagTrue(User user);
    Optional<Address> findByUserAndIsDefaultTrue(User user);
    Optional<Address> findByUserAndIsBillingTrue(User user);
    List<Address> findByActiveFlagFalse();
    void deleteByUserAndId(User user, Long id);
}
