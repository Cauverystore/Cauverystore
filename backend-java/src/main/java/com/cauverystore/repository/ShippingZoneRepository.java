package com.cauverystore.repository;

import com.cauverystore.entities.ShippingZone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Long> {
    List<ShippingZone> findByActiveTrue();
}
