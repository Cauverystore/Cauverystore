package com.cauverystore.service;

import com.cauverystore.entities.PlatformSetting;
import com.cauverystore.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingRepository repo;

    public List<PlatformSetting> getAllSettings() {
        return repo.findAll();
    }

    public List<PlatformSetting> getSettingsByCategory(String category) {
        return repo.findByCategory(category);
    }

    public PlatformSetting updateSetting(String key, String value) {
        PlatformSetting setting = repo.findById(key)
                .orElseGet(() -> {
                    PlatformSetting s = new PlatformSetting();
                    s.setSettingKey(key);
                    if (key.contains(".")) {
                        String cat = key.substring(0, key.indexOf('.')).toUpperCase();
                        s.setCategory(cat);
                    } else {
                        s.setCategory("GENERAL");
                    }
                    return s;
                });
        setting.setSettingValue(value);
        return repo.save(setting);
    }

    public List<PlatformSetting> updateSettings(Map<String, String> settings) {
        settings.forEach((key, value) -> updateSetting(key, value));
        return repo.findAll();
    }

    public PlatformSetting getSetting(String key) {
        return repo.findById(key).orElse(null);
    }
}
