package com.cauverystore.repository;

import com.cauverystore.entities.PincodeStateRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PincodeStateRangeRepository extends JpaRepository<PincodeStateRange, Long> {

    /**
     * Ranges covering a pincode prefix. Returns a list because ranges overlap - 396 belongs to
     * both Gujarat and Dadra and Nagar Haveli - so a single-result lookup would throw on the
     * very addresses that most need resolving.
     */
    List<PincodeStateRange> findByPrefixFromLessThanEqualAndPrefixToGreaterThanEqual(int a, int b);
}
