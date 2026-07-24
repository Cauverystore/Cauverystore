package com.cauverystore.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

@Service
public class CloudinaryService {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    private Cloudinary cloudinary;
    private boolean configured = false;

    @PostConstruct
    public void init() {
        if (cloudName != null && !cloudName.isEmpty() && apiKey != null && !apiKey.isEmpty() && apiSecret != null && !apiSecret.isEmpty()) {
            cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
            ));
            configured = true;
        }
    }

    public boolean isConfigured() { return configured; }

    public String uploadImage(MultipartFile file) {
        if (!configured) throw new RuntimeException("Cloudinary not configured");
        try {
            File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }
            Map<?, ?> result = cloudinary.uploader().upload(tempFile, ObjectUtils.emptyMap());
            tempFile.delete();
            return (String) result.get("secure_url");
        } catch (Exception e) {
            throw new RuntimeException("Cloudinary upload failed: " + e.getMessage());
        }
    }

    public String uploadFromUrl(String imageUrl) {
        if (!configured) throw new RuntimeException("Cloudinary not configured");
        try {
            Map<?, ?> result = cloudinary.uploader().upload(imageUrl, ObjectUtils.emptyMap());
            return (String) result.get("secure_url");
        } catch (Exception e) {
            throw new RuntimeException("Cloudinary upload from URL failed: " + e.getMessage());
        }
    }

    public void deleteImage(String publicId) {
        if (!configured) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception ignored) {}
    }
}
