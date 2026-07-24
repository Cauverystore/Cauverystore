package com.cauverystore.repository;

import com.cauverystore.entities.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findByActiveTrueAndPositionOrderBySortOrder(String position);
    List<Banner> findAllByOrderBySortOrder();
}
