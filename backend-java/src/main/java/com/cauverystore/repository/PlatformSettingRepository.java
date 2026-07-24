package com.cauverystore.repository;

import com.cauverystore.entities.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {

    List<PlatformSetting> findByCategory(String category);
}
