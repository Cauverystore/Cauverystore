package com.cauverystore.service;

import com.cauverystore.entities.AgentJob;
import com.cauverystore.repository.AgentJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Copies a Listing Agent draft's supplier photos onto the product, on the server.
 *
 * The browser is not involved, so it works the same in every browser. Only the photo links the
 * agent stored on the product's own draft are fetched (never a link sent by the caller), only over
 * https from the supplier hosts, without following redirects, and at most 10 MB each. Each photo
 * then goes through the normal image pipeline, so the store keeps its own resized copies.
 */
@Service
public class SupplierPhotoService {

    static final Set<String> PHOTO_HOSTS = Set.of("cdn.shopify.com", "deodap.in", "www.deodap.in",
            "baapstore.com", "www.baapstore.com");
    static final Map<String, String> TYPES = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    static final int MAX_PHOTOS = 5;
    static final int MAX_BYTES = 10 * 1024 * 1024;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentJobRepository jobs;
    private final ProductService products;
    private final String imageServiceUrl;
    private final RestTemplate imageService;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public SupplierPhotoService(AgentJobRepository jobs, ProductService products,
                                @Value("${app.image-service.url:http://localhost:9092}") String imageServiceUrl) {
        this.jobs = jobs;
        this.products = products;
        this.imageServiceUrl = imageServiceUrl;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(30000);
        this.imageService = new RestTemplate(f);
    }

    /** The supplier photo links stored for this product's draft (empty if it did not come from an agent). */
    public List<String> photosFor(Long productId) {
        if (productId == null) return List.of();
        return jobs.findFirstByProductIdOrderByIdDesc(productId).map(SupplierPhotoService::photoLinks).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    static List<String> photoLinks(AgentJob j) {
        if (j.getDraftJson() == null) return List.of();
        try {
            Map<String, Object> d = JSON.readValue(j.getDraftJson(), Map.class);
            if (!(d.get("imageUrls") instanceof List<?> l)) return List.of();
            return l.stream().map(String::valueOf).filter(SupplierPhotoService::allowed).distinct().limit(MAX_PHOTOS).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    static boolean allowed(String link) {
        try {
            URI u = URI.create(link);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null
                    && PHOTO_HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT)) && u.getUserInfo() == null
                    && (u.getPort() == -1 || u.getPort() == 443);
        } catch (Exception e) {
            return false;
        }
    }

    /** Copies the photos onto the product. Returns how many were added and why any failed. */
    public Map<String, Object> importFor(Long productId) {
        List<String> links = photosFor(productId);
        List<String> failed = new ArrayList<>();
        int added = 0;
        for (int i = 0; i < links.size(); i++) {
            String link = links.get(i);
            try {
                Photo p = download(link);
                Map<?, ?> sizes = upload(p, productId, "supplier-photo-" + (i + 1) + "." + TYPES.get(p.type()));
                products.attachImageUrls(productId, str(sizes.get("preview_url")), str(sizes.get("thumbnail_url")),
                        str(sizes.get("zoom_url")), str(sizes.get("original_url")));
                added++;
            } catch (Exception e) {
                failed.add("Photo " + (i + 1) + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("found", links.size());
        m.put("added", added);
        m.put("failed", failed);
        return m;
    }

    record Photo(byte[] bytes, String type) {}

    Photo download(String link) throws Exception {
        if (!allowed(link)) throw new IllegalArgumentException("not a supplier photo link");
        HttpRequest req = HttpRequest.newBuilder(URI.create(link)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (compatible; CauveryStore/1.0)").GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) throw new IllegalStateException("the supplier answered " + res.statusCode());
            String type = res.headers().firstValue("Content-Type").orElse("").split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (!TYPES.containsKey(type)) throw new IllegalStateException("not a JPG, PNG or WebP photo");
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("larger than 10 MB");
            if (bytes.length == 0) throw new IllegalStateException("empty file");
            return new Photo(bytes, type);
        }
    }

    private Map<?, ?> upload(Photo p, Long productId, String name) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders part = new HttpHeaders();
        part.setContentType(MediaType.parseMediaType(p.type()));
        body.add("image", new HttpEntity<>(new ByteArrayResource(p.bytes()) {
            @Override
            public String getFilename() {
                return name;
            }
        }, part));
        body.add("product_id", String.valueOf(productId));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> resp = imageService.postForEntity(imageServiceUrl + "/upload-image",
                new HttpEntity<>(body, headers), Map.class);
        if (resp.getBody() == null || resp.getBody().get("preview_url") == null) {
            throw new IllegalStateException("the image server did not accept it");
        }
        return resp.getBody();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
