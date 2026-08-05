package com.cauverystore.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Serves /uploads/** from the Node image-service (where processed WebP files
// live on a persistent volume), falling back to the local disk for legacy
// images uploaded before the pipeline. The frontend keeps loading images from
// the backend domain, so no frontend/env changes are needed in production.
@RestController
@RequestMapping("/uploads")
public class UploadsController {

    private final RestTemplate restTemplate;
    private final String imageServiceUrl;
    private final Path localUploadRoot;

    public UploadsController(@Value("${app.image-service.url:http://localhost:9092}") String imageServiceUrl,
                             @Value("${app.upload.dir:uploads}") String localUploadDir) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.imageServiceUrl = imageServiceUrl;
        this.localUploadRoot = Paths.get(localUploadDir).toAbsolutePath().normalize();
    }

    @GetMapping("/**")
    public ResponseEntity<byte[]> serve(HttpServletRequest request) {
        String name = relativeName(request.getRequestURI());
        if (name == null) {
            return ResponseEntity.badRequest().build();
        }

        // 1) Image-service first (persistent volume, WebP pipeline output).
        try {
            ResponseEntity<byte[]> nodeResp = restTemplate.getForEntity(
                    imageServiceUrl + "/uploads/" + name, byte[].class);
            HttpHeaders headers = new HttpHeaders();
            if (nodeResp.getHeaders().getContentType() != null) {
                headers.setContentType(nodeResp.getHeaders().getContentType());
            }
            return new ResponseEntity<>(nodeResp.getBody(), headers, nodeResp.getStatusCode());
        } catch (Exception nodeErr) {
            // node 404 (file is legacy/local-only) or unreachable -> fall back to disk
        }

        // 2) Legacy local uploads.
        try {
            String decoded = UriUtils.decode(name, StandardCharsets.UTF_8);
            if (decoded.isEmpty() || decoded.contains("..")) {
                return ResponseEntity.badRequest().build();
            }
            Path target = localUploadRoot.resolve(decoded).normalize();
            if (!target.getParent().startsWith(localUploadRoot)) {
                return ResponseEntity.badRequest().build();
            }
            if (!Files.isRegularFile(target)) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = Files.readAllBytes(target);
            return ResponseEntity.ok().contentType(mediaType(decoded)).body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String relativeName(String requestUri) {
        int idx = requestUri.indexOf("/uploads/");
        if (idx < 0) return null;
        String name = requestUri.substring(idx + "/uploads/".length());
        return name.isEmpty() ? null : name;
    }

    private MediaType mediaType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
