package com.cauverystore.repository;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUser(User user);

    // NULL active_flag is treated as active: rows predating the soft-delete column were
    // backfilled by AddressActiveFlagMigrator, but until that runs (or if a row slips through)
    // a NULL must not silently hide an address from the book or the dedupe search.
    @Query("SELECT a FROM Address a WHERE a.user = :user AND (a.activeFlag = true OR a.activeFlag IS NULL)")
    List<Address> findActiveByUser(@Param("user") User user);

    Optional<Address> findByUserAndIsDefaultTrue(User user);
    Optional<Address> findByUserAndIsBillingTrue(User user);
    List<Address> findByActiveFlagFalse();
    void deleteByUserAndId(User user, Long id);
}
