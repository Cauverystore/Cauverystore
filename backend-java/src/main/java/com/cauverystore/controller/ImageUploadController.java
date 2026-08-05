package com.cauverystore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

// Authenticated gateway to the Node image-service pipeline (POST /upload-image).
// The frontend stays on a single origin (dev: http://localhost:9091, prod: nginx),
// auth is enforced here before the file ever reaches the image service, and the
// 5MB/type/path checks are done twice: by this controller's multipart parsing
// (Spring, 10MB cap) and by multer + magic-byte sniffing in the image service.
@RestController
@RequestMapping("/api/uploads")
public class ImageUploadController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String imageServiceUrl;

    public ImageUploadController(@Value("${app.image-service.url:http://localhost:9092}") String imageServiceUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
        this.imageServiceUrl = imageServiceUrl;
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<?> uploadImage(@RequestParam("image") MultipartFile file,
                                         @RequestParam("product_id") String productId) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        byte[] bytes = file.getBytes();
        body.add("image", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });
        body.add("product_id", productId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    imageServiceUrl + "/upload-image", request, Map.class);
            return ResponseEntity.status(resp.getStatusCode()).body(resp.getBody());
        } catch (HttpClientErrorException e) {
            try {
                Map<?, ?> error = objectMapper.readValue(e.getResponseBodyAsString(), Map.class);
                return ResponseEntity.status(e.getStatusCode()).body(error);
            } catch (Exception parseErr) {
                return ResponseEntity.status(e.getStatusCode())
                        .body(Map.of("error", e.getStatusText()));
            }
        }
    }
}
