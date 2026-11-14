package com.cauverystore.erp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin REST client for ERPNext / Frappe. Uses only the JDK HTTP client and Jackson, so it adds
 * no dependencies. Authenticates with an API key + secret generated on an ERPNext user.
 */
@Component
public class ErpNextClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)   // plain-http HTTP/2 upgrade can stall behind nginx
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${erpnext.enabled:false}") private boolean enabled;
    @Value("${erpnext.base-url:}") private String baseUrl;
    @Value("${erpnext.api-key:}") private String apiKey;
    @Value("${erpnext.api-secret:}") private String apiSecret;

    public static class ErpException extends RuntimeException {
        public ErpException(String message) { super(message); }
    }

    public boolean isConfigured() {
        return enabled && !baseUrl.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode call(String method, String path, Object body) {
        if (!isConfigured()) throw new ErpException("ERPNext is not configured (erpnext.enabled / base-url / api-key / api-secret)");
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "token " + apiKey + ":" + apiSecret)
                    .header("Accept", "application/json");
            if (body != null) {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 404) return null;
            if (r.statusCode() >= 300) {
                String text = r.body() == null ? "" : r.body();
                throw new ErpException("ERPNext " + method + " " + path + " -> " + r.statusCode() + ": "
                        + (text.length() > 600 ? text.substring(0, 600) : text));
            }
            return mapper.readTree(r.body());
        } catch (IOException e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            throw new ErpException("ERPNext unreachable at " + baseUrl + " (" + e.getClass().getSimpleName()
                    + ": " + e.getMessage() + "; root cause " + root.getClass().getSimpleName() + ": " + root.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErpException("Interrupted calling ERPNext");
        }
    }

    /** The document, or null when it does not exist. */
    public JsonNode get(String doctype, String name) {
        JsonNode n = call("GET", "/api/resource/" + enc(doctype) + "/" + enc(name), null);
        return n == null ? null : n.get("data");
    }

    public boolean exists(String doctype, String name) { return get(doctype, name) != null; }

    /** Creates the document and returns what ERPNext stored (including its generated name). */
    public JsonNode create(String doctype, Map<String, Object> doc) {
        JsonNode n = call("POST", "/api/resource/" + enc(doctype), doc);
        return n == null ? null : n.get("data");
    }

    /** Submits a draft (docstatus 0 -> 1). */
    public JsonNode submit(String doctype, String name) {
        JsonNode n = call("PUT", "/api/resource/" + enc(doctype) + "/" + enc(name), Map.of("docstatus", 1));
        return n == null ? null : n.get("data");
    }

    /** Deletes a draft. */
    public void delete(String doctype, String name) {
        call("DELETE", "/api/resource/" + enc(doctype) + "/" + enc(name), null);
    }

    /** Cancels a submitted document (docstatus 1 -> 2). */
    public void cancel(String doctype, String name) {
        call("POST", "/api/method/frappe.client.cancel", Map.of("doctype", doctype, "name", name));
    }

    /** Changes fields on an existing document (drafts, masters and settings). */
    public JsonNode update(String doctype, String name, Map<String, Object> fields) {
        JsonNode n = call("PUT", "/api/resource/" + enc(doctype) + "/" + enc(name), fields);
        return n == null ? null : n.get("data");
    }

    /**
     * Documents matching simple equality filters, with the listed fields.
     * filters: field -> value, all must match.
     */
    public JsonNode list(String doctype, Map<String, String> filters, List<String> fields, int limit) {
        StringBuilder f = new StringBuilder("[");
        for (Map.Entry<String, String> e : filters.entrySet()) {
            if (f.length() > 1) f.append(',');
            f.append("[\"").append(e.getKey().replace("\"", "")).append("\",\"=\",\"")
                    .append(String.valueOf(e.getValue()).replace("\"", "")).append("\"]");
        }
        f.append(']');
        StringBuilder fl = new StringBuilder("[");
        for (String field : fields) {
            if (fl.length() > 1) fl.append(',');
            fl.append('"').append(field.replace("\"", "")).append('"');
        }
        fl.append(']');
        JsonNode n = call("GET", "/api/resource/" + enc(doctype) + "?fields=" + enc(fl.toString())
                + "&limit_page_length=" + limit + "&filters=" + enc(f.toString()), null);
        return n == null ? mapper.createArrayNode() : n.path("data");
    }

    /** Returns the first document name matching a field value, or null. */
    public String findNameByField(String doctype, String field, String value) {
        String filters = "[[\"" + field + "\",\"=\",\"" + value.replace("\"", "") + "\"]]";
        JsonNode n = call("GET", "/api/resource/" + enc(doctype) + "?fields=%5B%22name%22%5D&limit_page_length=1&filters="
                + enc(filters), null);
        if (n == null || !n.has("data") || n.get("data").isEmpty()) return null;
        return n.get("data").get(0).get("name").asText();
    }
}
