package com.cauverystore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Thin HTTP wrapper around Shiprocket's external API.
 *
 * Shiprocket hands out a bearer token from /auth/login that lasts 10 days. It is kept here and
 * renewed a day early, and once more on a 401 in case Shiprocket revoked it sooner.
 */
@Component
public class ShiprocketClient {

    private static final Duration TOKEN_LIFETIME = Duration.ofDays(9);

    private final String email;
    private final String password;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public ShiprocketClient(@Value("${shiprocket.email:}") String email,
                            @Value("${shiprocket.password:}") String password,
                            @Value("${shiprocket.base-url:https://apiv2.shiprocket.in/v1/external}") String baseUrl) {
        this.email = email;
        this.password = password;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    public JsonNode post(String path, Object body) {
        return call("POST", path, body, true);
    }

    public JsonNode get(String path) {
        return call("GET", path, null, true);
    }

    private JsonNode call(String method, String path, Object body, boolean retryOnAuthFailure) {
        if (!isConfigured()) {
            throw new IllegalStateException("Shiprocket is not set up - SHIPROCKET_EMAIL and SHIPROCKET_PASSWORD are empty");
        }
        HttpResponse<String> res = send(method, path, body, currentToken());
        if (res.statusCode() == 401 && retryOnAuthFailure) {
            synchronized (this) { tokenExpiresAt = Instant.EPOCH; }
            return call(method, path, body, false);
        }
        JsonNode json = parse(res.body());
        if (res.statusCode() >= 400) {
            throw new ShiprocketException(res.statusCode(), describeError(json, res.body()));
        }
        return json;
    }

    private synchronized String currentToken() {
        if (token == null || Instant.now().isAfter(tokenExpiresAt)) {
            HttpResponse<String> res = send("POST", "/auth/login", Map.of("email", email, "password", password), null);
            JsonNode json = parse(res.body());
            if (res.statusCode() >= 400 || !json.hasNonNull("token")) {
                throw new ShiprocketException(res.statusCode(),
                        "Shiprocket login failed - check the API user's email and password ("
                                + describeError(json, res.body()) + ")");
            }
            token = json.get("token").asText();
            tokenExpiresAt = Instant.now().plus(TOKEN_LIFETIME);
        }
        return token;
    }

    private HttpResponse<String> send(String method, String path, Object body, String bearer) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (bearer != null) req.header("Authorization", "Bearer " + bearer);
            if ("POST".equals(method)) {
                req.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                req.GET();
            }
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShiprocketException(0, "Interrupted while calling Shiprocket");
        } catch (Exception e) {
            throw new ShiprocketException(0, "Could not reach Shiprocket: " + e.getMessage());
        }
    }

    private JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
        } catch (Exception e) {
            return mapper.createObjectNode().put("message", body);
        }
    }

    private static String describeError(JsonNode json, String raw) {
        if (json.hasNonNull("message")) {
            String msg = json.get("message").asText();
            if (json.has("errors")) msg += " " + json.get("errors");
            return msg;
        }
        return raw == null ? "no response" : raw.length() > 300 ? raw.substring(0, 300) : raw;
    }

    public static class ShiprocketException extends RuntimeException {
        private final int status;

        public ShiprocketException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() { return status; }
    }
}
